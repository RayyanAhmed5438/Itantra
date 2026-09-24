@file:Suppress("DEPRECATION")

package com.tactical.platform.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.ble.BleConnectionManager
import com.tactical.platform.api.ble.BleDiagnostics
import com.tactical.platform.api.ble.BleLinkState
import com.tactical.platform.radio.BleConnectionRegistry
import com.tactical.platform.radio.BleRadioTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** App-level squad membership plus persistent BLE GATT client sessions. Android bonding is not used. */
@SuppressLint("MissingPermission")
class AndroidBleConnectionManager(
    private val context: Context,
    private val registry: BleConnectionRegistry,
    private val localDeviceId: String
) : BleConnectionManager, BleConnectionRegistry.ConnectionListener {

    private val states = ConcurrentHashMap<String, MutableStateFlow<BleLinkState>>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TacticalResult<Unit>>>()
    private val gattClients = ConcurrentHashMap<String, BluetoothGatt>()
    private val rssiStates = ConcurrentHashMap<String, MutableStateFlow<Int?>>()
    private val rssiJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val reconnectScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    private var lastAdvertisingOk = false
    private var lastScanningOk = false
    private var emptyCycles = 0
    private var scanFailures = 0
    private var lastRecovery = 0L

    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                android.util.Log.d(TAG, "Bluetooth turned off; marking BLE links disconnected")
                gattClients.values.toList().forEach { gatt ->
                    try { gatt.disconnect() } catch (_: Exception) {}
                    try { gatt.close() } catch (_: Exception) {}
                }
                gattClients.clear()
                rssiJobs.values.toList().forEach { it.cancel() }
                rssiJobs.clear()
                registry.allConnectedAddresses().toList().forEach { registry.unregisterOutboundConnection(it) }
                states.keys.toList().forEach { setState(it, BleLinkState.DISCONNECTED) }
            } else if (state == BluetoothAdapter.STATE_ON) {
                android.util.Log.d(TAG, "Bluetooth turned on; reconnecting squad members")
                reconnectScope.launch {
                    delay(1500L)
                    squadDeviceIds().forEach { id ->
                        runCatching { reconnectSquadMember(id) }
                    }
                }
            }
        }
    }

    init {
        registry.addConnectionListener(this)

        // Startup GATT can race with the peer's GATT server initialization.
        // Retry quietly every 10 seconds; established sessions are left alone.
        reconnectScope.launch {
            delay(3000L)
            while (true) {
                val adapter = runCatching {
                    context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                }.getOrNull()

                if (adapter?.isEnabled == true) {
                    squadDeviceIds().forEach { id ->
                        runCatching { reconnectSquadMember(id) }
                    }
                }

                delay(10_000L)
            }
        }

        val adapterFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(adapterStateReceiver, adapterFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(adapterStateReceiver, adapterFilter)
        }
    }

    override suspend fun addToSquad(deviceAddress: String): TacticalResult<Unit> {
        if (!hasConnectPermission()) {
            return TacticalResult.Failure("Missing BLUETOOTH_CONNECT permission")
        }

        val resolvedAddress = resolveAddress(deviceAddress)
            ?: return TacticalResult.Failure("BLE address not known yet; scan for the device again")

        val appId = BlePeerAddressRegistry.applicationIdFor(resolvedAddress)
            ?: deviceAddress

        rememberAddress(appId, resolvedAddress)

        val ids = squadDeviceIds().toMutableSet()
        ids.add(appId)
        prefs.edit()
            .putStringSet(SQUAD_IDS_KEY, ids)
            .remove(LEGACY_PAIRED_IDS_KEY)
            .apply()

        setState(resolvedAddress, if (hasDirectConnection(resolvedAddress)) {
            BleLinkState.CONNECTED
        } else {
            BleLinkState.AVAILABLE
        })

        android.util.Log.d(TAG, "Added " + appId + " to iTantra squad (no Android pairing)")
        return TacticalResult.Success(Unit)
    }

    override suspend fun removeFromSquad(deviceAddress: String) {
        val resolvedAddress = resolveAddress(deviceAddress) ?: deviceAddress
        val appId = applicationIdForAddress(resolvedAddress) ?: deviceAddress
        val ids = squadDeviceIds().toMutableSet()

        if (ids.remove(appId)) {
            prefs.edit()
                .putStringSet(SQUAD_IDS_KEY, ids)
                .remove(LEGACY_PAIRED_IDS_KEY)
                .apply()
        }

        // Squad membership and physical GATT connectivity are intentionally
        // independent. Keep the GATT link available for mesh participation.
        refreshLinkState(resolvedAddress, BleLinkState.AVAILABLE)
        android.util.Log.d(TAG, "Removed " + appId + " from iTantra squad")
    }

    override suspend fun connect(deviceAddress: String): TacticalResult<Unit> {
        if (!hasConnectPermission()) return TacticalResult.Failure("Missing BLUETOOTH_CONNECT permission")

        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            setState(deviceAddress, BleLinkState.DISCONNECTED)
            return TacticalResult.Failure("Bluetooth is off")
        }

        val resolvedAddress = resolveAddress(deviceAddress)
            ?: return TacticalResult.Failure("BLE address not known yet; scan for the device again")
        val device = deviceForAddress(resolvedAddress)
            ?: return TacticalResult.Failure("Bluetooth device not found")

        // The app-level paired list is the explicit pairing gate.
        // Android's system bond may be absent after a system/app reset; BLE GATT
        // itself can still establish the transport without requiring that bond.
        // A peer can already be connected in the opposite GATT role:
        // this device is the server and the peer is the client. That link is
        // already bidirectional for iTantra (peer writes to us; we notify it),
        // so do not create a duplicate outbound session.
        if (registry.inboundDevice(resolvedAddress) != null) {
            setState(resolvedAddress, BleLinkState.CONNECTED)
            return TacticalResult.Success(Unit)
        }

        // gattClients contains only fully initialized/ready outbound GATT sessions.
        gattClients[resolvedAddress]?.let {
            setState(resolvedAddress, BleLinkState.CONNECTED)
            return TacticalResult.Success(Unit)
        }

        suspend fun awaitExisting(
            existing: CompletableDeferred<TacticalResult<Unit>>
        ): TacticalResult<Unit> {
            return try {
                withTimeout(20_000L) { existing.await() }
            } catch (_: TimeoutCancellationException) {
                TacticalResult.Failure("Existing GATT connection attempt timed out")
            }
        }

        pending[resolvedAddress]?.let { existing ->
            return awaitExisting(existing)
        }

        val completion = CompletableDeferred<TacticalResult<Unit>>()
        val existing = pending.putIfAbsent(resolvedAddress, completion)
        if (existing != null) {
            return awaitExisting(existing)
        }

        fun failConnection(
            targetGatt: BluetoothGatt?,
            message: String,
            state: BleLinkState = BleLinkState.FAILED
        ) {
            if (targetGatt != null) {
                gattClients.remove(resolvedAddress, targetGatt)
                rssiJobs.remove(resolvedAddress)?.cancel()
                registry.unregisterOutboundConnection(resolvedAddress)
            }
            if (pending.remove(resolvedAddress, completion)) {
                completion.complete(TacticalResult.Failure(message))
            }

            setState(
                resolvedAddress,
                if (hasDirectConnection(resolvedAddress)) {
                    BleLinkState.CONNECTED
                } else {
                    state
                }
            )
            if (targetGatt != null) {
                try { targetGatt.disconnect() } catch (_: Exception) {}
                try { targetGatt.close() } catch (_: Exception) {}
            }
        }

        setState(resolvedAddress, BleLinkState.CONNECTING)
        android.util.Log.d(
            TAG,
            "GATT connect requested for " + resolvedAddress +
                " (squad=true)
        )

        return try {
            val callback = object : BluetoothGattCallback() {

                private fun isCurrentGatt(gatt: BluetoothGatt): Boolean =
                    pending[resolvedAddress] === completion || gattClients[resolvedAddress] === gatt

                override fun onConnectionStateChange(
                    gatt: BluetoothGatt,
                    status: Int,
                    newState: Int
                ) {
                    if (!isCurrentGatt(gatt)) {
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    if (newState == BluetoothProfile.STATE_CONNECTED &&
                        status == BluetoothGatt.GATT_SUCCESS
                    ) {
                        setState(resolvedAddress, BleLinkState.CONNECTING)
                        android.util.Log.d(
                            TAG,
                            "GATT connected, discovering services for " + resolvedAddress
                        )

                        val started = try {
                            gatt.discoverServices()
                        } catch (_: Exception) {
                            false
                        }

                        if (!started) {
                            failConnection(
                                gatt,
                                "GATT service discovery could not start"
                            )
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gattClients.remove(resolvedAddress, gatt)
                        registry.unregisterOutboundConnection(resolvedAddress)
                        rssiJobs.remove(resolvedAddress)?.cancel()
                        if (pending.remove(resolvedAddress, completion)) {
                            completion.complete(
                                TacticalResult.Failure("GATT disconnected: status=$status")
                            )
                        }

                        // Do not report DISCONNECTED when the same peer is
                        // still connected through the inbound GATT role.
                        refreshLinkState(
                            resolvedAddress,
                            if (squadDeviceIds().contains(
                                    applicationIdForAddress(resolvedAddress)
                                )
                            ) {
                                BleLinkState.DISCONNECTED
                            } else {
                                BleLinkState.AVAILABLE
                            }
                        )
                        try { gatt.close() } catch (_: Exception) {}
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (!isCurrentGatt(gatt)) {
                        try { gatt.close() } catch (_: Exception) {}
                        return
                    }

                    android.util.Log.d(
                        TAG,
                        "GATT services discovered for " + resolvedAddress + ", status=" + status
                    )

                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        failConnection(
                            gatt,
                            "GATT service discovery failed: $status"
                        )
                        return
                    }

                    val service = gatt.getService(BleRadioTransport.GATT_SERVICE_UUID)
                    val characteristic =
                        service?.getCharacteristic(BleRadioTransport.PACKET_CHARACTERISTIC_UUID)

                    if (characteristic == null) {
                        failConnection(
                            gatt,
                            "iTantra GATT service not found"
                        )
                        return
                    }

                    if (!gatt.setCharacteristicNotification(characteristic, true)) {
                        failConnection(
                            gatt,
                            "Could not enable notifications"
                        )
                        return
                    }

                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor == null) {
                        failConnection(
                            gatt,
                            "CCCD descriptor missing"
                        )
                        return
                    }

                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val rc = gatt.writeDescriptor(
                                descriptor,
                                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            )
                            if (rc != BluetoothStatusCodes.SUCCESS) {
                                failConnection(
                                    gatt,
                                    "Notification descriptor write failed: $rc"
                                )
                            }
                        } else {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (!gatt.writeDescriptor(descriptor)) {
                                failConnection(
                                    gatt,
                                    "Notification descriptor write failed"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        failConnection(
                            gatt,
                            "Notification setup failed: ${e.message}"
                        )
                    }
                }

                override fun onDescriptorWrite(
                    gatt: BluetoothGatt,
                    descriptor: BluetoothGattDescriptor,
                    status: Int
                ) {
                    if (!isCurrentGatt(gatt) || descriptor.uuid != CCCD_UUID) return

                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        // The GATT becomes visible to the transport only after
                        // service discovery + CCCD configuration have both succeeded.
                        gattClients[resolvedAddress] = gatt
                        registry.registerOutboundConnection(gatt)
                        setState(resolvedAddress, BleLinkState.CONNECTED)
                        startRssiPolling(resolvedAddress, gatt)
                        android.util.Log.d(
                            TAG,
                            "GATT ready for " + resolvedAddress
                        )
                        if (pending.remove(resolvedAddress, completion)) {
                            completion.complete(TacticalResult.Success(Unit))
                        }
                    } else {
                        failConnection(
                            gatt,
                            "CCCD write status=$status"
                        )
                    }
                }

                @Deprecated("Use the value overload on API 33+.")
                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic
                ) {
                    if (gattClients[resolvedAddress] === gatt &&
                        characteristic.uuid == BleRadioTransport.PACKET_CHARACTERISTIC_UUID
                    ) {
                        android.util.Log.d(
                            TAG,
                            "Incoming BLE notification from " + resolvedAddress +
                                ", bytes=" + characteristic.value.size
                        )
                        registry.dispatchRawIncoming(
                            resolvedAddress,
                            characteristic.value
                        )
                    }
                }

                override fun onCharacteristicChanged(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray
                ) {
                    if (gattClients[resolvedAddress] === gatt &&
                        characteristic.uuid == BleRadioTransport.PACKET_CHARACTERISTIC_UUID
                    ) {
                        android.util.Log.d(
                            TAG,
                            "Incoming BLE notification from " + resolvedAddress +
                                ", bytes=" + value.size
                        )
                        registry.dispatchRawIncoming(resolvedAddress, value)
                    }
                }

                override fun onReadRemoteRssi(
                    gatt: BluetoothGatt,
                    rssi: Int,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS &&
                        gattClients[resolvedAddress] === gatt
                    ) {
                        registry.updateRssi(resolvedAddress, rssi)
                        rssiState(resolvedAddress).value = rssi
                    }
                }

                override fun onMtuChanged(
                    gatt: BluetoothGatt,
                    mtu: Int,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS &&
                        (gattClients[resolvedAddress] === gatt || pending[resolvedAddress] === completion)
                    ) {
                        registry.onMtuNegotiated(resolvedAddress, mtu)
                    }
                }
            }

            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    context,
                    false,
                    callback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(context, false, callback)
            }

            try {
                withTimeout(20_000L) { completion.await() }
            } catch (_: TimeoutCancellationException) {
                if (pending.remove(resolvedAddress, completion) != null) {
                    setState(resolvedAddress, BleLinkState.FAILED)
                }
                gattClients.remove(resolvedAddress, gatt)
                rssiJobs.remove(resolvedAddress)?.cancel()
                registry.unregisterOutboundConnection(resolvedAddress)
                try { gatt.disconnect() } catch (_: Exception) {}
                try { gatt.close() } catch (_: Exception) {}
                TacticalResult.Failure("GATT connection timed out")
            }
        } catch (e: Exception) {
            if (pending.remove(resolvedAddress, completion) != null) {
                setState(resolvedAddress, BleLinkState.FAILED)
            }
            TacticalResult.Failure("Unable to connect: ${e.message}")
        }
    }

    override suspend fun disconnect(deviceAddress: String) {
        val resolvedAddress = resolveAddress(deviceAddress) ?: deviceAddress
        gattClients.remove(resolvedAddress)?.let {
            try { it.disconnect() } catch (_: Exception) {}
            try { it.close() } catch (_: Exception) {}
        }
        registry.unregisterOutboundConnection(resolvedAddress)
        refreshLinkState(
            resolvedAddress,
            if (squadDeviceIds().contains(
                    applicationIdForAddress(resolvedAddress)
                )
            ) {
                BleLinkState.DISCONNECTED
            } else {
                BleLinkState.AVAILABLE
            }
        )
    }

    override fun state(deviceAddress: String): Flow<BleLinkState> = stateFlow(resolveAddress(deviceAddress) ?: deviceAddress).asStateFlow()

    override fun rssi(deviceAddress: String): Flow<Int?> =
        rssiState(resolveAddress(deviceAddress) ?: deviceAddress).asStateFlow()

    override fun squadDeviceIds(): Set<String> {
        val current = prefs.getStringSet(SQUAD_IDS_KEY, null)
        if (current != null) return current.toSet()

        // Migrate the previous app-level paired list once. These IDs are
        // treated only as squad membership; no Android bond is required.
        val legacy = prefs.getStringSet(LEGACY_PAIRED_IDS_KEY, emptySet())?.toSet() ?: emptySet()
        if (legacy.isNotEmpty()) {
            prefs.edit()
                .putStringSet(SQUAD_IDS_KEY, legacy)
                .remove(LEGACY_PAIRED_IDS_KEY)
                .apply()
        }
        return legacy
    }

    override suspend fun reconnectSquadMember(deviceAddress: String): TacticalResult<Unit> {
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            setState(deviceAddress, BleLinkState.DISCONNECTED)
            return TacticalResult.Failure("Bluetooth is off")
        }

        val resolvedAddress = resolveAddress(deviceAddress)
            ?: return TacticalResult.Failure("BLE address not known yet; scan for the device again")

        // Prefer an already-established inbound GATT session. The local
        // server can notify that client, while the client can write back to
        // this server, so another outbound session is unnecessary.
        if (registry.inboundDevice(resolvedAddress) != null) {
            setState(resolvedAddress, BleLinkState.CONNECTED)
            return TacticalResult.Success(Unit)
        }

        if (gattClients.containsKey(resolvedAddress)) {
            setState(resolvedAddress, BleLinkState.CONNECTED)
            return TacticalResult.Success(Unit)
        }

        val device = deviceForAddress(resolvedAddress)
            ?: return TacticalResult.Failure("Bluetooth device not found")
        return connect(deviceAddress)
    }

    override suspend fun repairAndReconnect(deviceAddress: String): TacticalResult<Unit> {
        // "Repair" now means reset and re-establish the GATT session. It never
        // invokes Android Bluetooth bonding.
        disconnect(deviceAddress)
        return connect(deviceAddress)
    }

    override fun onInboundConnected(device: BluetoothDevice) {
        // The scan/address registry is intentionally in-memory, so after
        // process recreation an inbound GATT can arrive before discovery has
        // repopulated it. Recover the stable iTantra ID from the persisted
        // appId -> address mapping as a fallback.
        val appId = applicationIdForAddress(device.address) ?: return
        rememberAddress(appId, device.address)
        setState(device.address, BleLinkState.CONNECTED)
        android.util.Log.d(
            TAG,
            "Inbound BLE link ready for " + appId +
                " (no Android pairing)"
        )
    }

    override fun onInboundDisconnected(device: BluetoothDevice) {
        // The peer may still have an outbound GATT session to this device.
        // Compute the state from both GATT roles instead of blindly marking
        // the address disconnected.
        val appId = applicationIdForAddress(device.address)
        refreshLinkState(
            device.address,
            if (appId != null && squadDeviceIds().contains(appId)) {
                BleLinkState.DISCONNECTED
            } else {
                BleLinkState.AVAILABLE
            }
        )
        android.util.Log.d(
            TAG,
            "Inbound BLE link disconnected for " + (appId ?: device.address)
        )
    }

    override fun diagnostics(): Flow<BleDiagnostics> = MutableStateFlow(
        BleDiagnostics(lastAdvertisingOk, lastScanningOk, registry.allConnectedAddresses().size, emptyCycles, scanFailures, lastRecovery, if (scanFailures >= 3) "BLE scan has failed repeatedly" else null)
    ).asStateFlow()

    fun updateDiscoveryHealth(advertisingOk: Boolean, scanningOk: Boolean, emptyCycle: Boolean) {
        lastAdvertisingOk = advertisingOk
        lastScanningOk = scanningOk
        scanFailures = if (scanningOk) 0 else scanFailures + 1
        emptyCycles = if (emptyCycle) emptyCycles + 1 else 0
    }

    fun markRecovery(epochMs: Long) {
        lastRecovery = epochMs
        scanFailures = 0
        emptyCycles = 0
    }

    private fun rememberAddress(appId: String, address: String) {
        prefs.edit()
            .putString(PREF_ADDRESS_PREFIX + appId, address)
            .apply()
        BlePeerAddressRegistry.remember(appId, address)
    }

    private fun rssiState(address: String): MutableStateFlow<Int?> =
        rssiStates.computeIfAbsent(address) { MutableStateFlow(null) }

    private fun startRssiPolling(address: String, gatt: BluetoothGatt) {
        rssiJobs.remove(address)?.cancel()
        rssiJobs[address] = reconnectScope.launch {
            while (gattClients[address] === gatt) {
                try {
                    gatt.readRemoteRssi()
                } catch (_: Exception) {
                    // A failed RSSI read is transient.
                }
                delay(2500L)
            }
        }
    }

    private fun resolveAddress(identifier: String): String? {
        return BlePeerAddressRegistry.addressFor(identifier)
            ?: prefs.getString(PREF_ADDRESS_PREFIX + identifier, null)
    }

    private fun applicationIdForAddress(address: String): String? {
        BlePeerAddressRegistry.applicationIdFor(address)?.let { return it }

        // Recover the stable app id even before discovery has refreshed the
        // in-memory address registry.
        return squadDeviceIds().firstOrNull { id ->
            prefs.getString(PREF_ADDRESS_PREFIX + id, null) == address
        }
    }

    private fun hasDirectConnection(address: String): Boolean =
        registry.inboundDevice(address) != null ||
            registry.outboundGatt(address) != null

    private fun refreshLinkState(address: String, disconnectedState: BleLinkState) {
        if (hasDirectConnection(address)) {
            setState(address, BleLinkState.CONNECTED)
        } else {
            setState(address, disconnectedState)
        }
    }

    private fun rememberSquadMember(identifier: String, address: String) {
        val appId = BlePeerAddressRegistry.applicationIdFor(address) ?: identifier
        val ids = squadDeviceIds().toMutableSet()
        ids.add(appId)
        prefs.edit()
            .putStringSet(SQUAD_IDS_KEY, ids)
            .remove(LEGACY_PAIRED_IDS_KEY)
            .putString(PREF_ADDRESS_PREFIX + appId, address)
            .apply()
        BlePeerAddressRegistry.remember(appId, address)
    }

    private fun forgetSquadMember(appId: String) {
        val ids = squadDeviceIds().toMutableSet()
        if (ids.remove(appId)) {
            prefs.edit()
                .putStringSet(SQUAD_IDS_KEY, ids)
                .remove(LEGACY_PAIRED_IDS_KEY)
                .remove(PREF_ADDRESS_PREFIX + appId)
                .apply()
        }
    }

    private fun stateFlow(address: String): MutableStateFlow<BleLinkState> = states.computeIfAbsent(address) { MutableStateFlow(initialState(address)) }
    private fun setState(address: String, state: BleLinkState) { stateFlow(address).value = state }
    private fun initialState(address: String): BleLinkState =
        if (hasDirectConnection(address)) BleLinkState.CONNECTED else BleLinkState.AVAILABLE
    private fun deviceForAddress(identifier: String): BluetoothDevice? {
        if (!hasConnectPermission()) return null
        return try {
            val address = resolveAddress(identifier) ?: return null
            context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                ?.adapter
                ?.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }
    }
    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun isBluetoothAddress(value: String): Boolean =
        value.matches(Regex("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"))

    companion object {
        private const val TAG = "AndroidBleConnection"
        private const val PREFS_NAME = "itantra_ble_links"
        private const val SQUAD_IDS_KEY = "squad_device_ids"
        private const val LEGACY_PAIRED_IDS_KEY = "paired_device_ids"
        private const val PREF_ADDRESS_PREFIX = "address_"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}