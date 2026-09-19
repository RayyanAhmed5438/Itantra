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

/** Explicit pairing and persistent BLE GATT client sessions. */
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

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> {
                    val appId = BlePeerAddressRegistry.applicationIdFor(device.address)
                    if (appId != null) {
                        rememberPairedPeer(appId, device.address)
                        setState(device.address, BleLinkState.PAIRED)
                        // Only the deterministic initiator establishes the
                        // outbound GATT client session. The other phone stays passive
                        // and uses the same GATT link in the opposite direction via
                        // server notifications.
                        reconnectScope.launch {
                            delay(500L)
                            runCatching { reconnectPaired(appId) }
                        }
                    } else {
                        setState(device.address, BleLinkState.PAIRED)
                    }
                }
                BluetoothDevice.BOND_NONE -> {
                    val appId = BlePeerAddressRegistry.applicationIdFor(device.address)
                    if (appId != null) {
                        forgetPairedPeer(appId)
                    }
                    setState(device.address, BleLinkState.NOT_PAIRED)
                }
            }
        }
    }

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
                android.util.Log.d(TAG, "Bluetooth turned on; reconnecting paired iTantra peers")
                reconnectScope.launch {
                    delay(1500L)
                    pairedDeviceIds().forEach { id ->
                        runCatching { reconnectPaired(id) }
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
                    pairedDeviceIds().forEach { id ->
                        runCatching { reconnectPaired(id) }
                    }
                }

                delay(10_000L)
            }
        }

        val bondFilter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        val adapterFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bondReceiver, bondFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(adapterStateReceiver, adapterFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(bondReceiver, bondFilter)
            @Suppress("DEPRECATION")
            context.registerReceiver(adapterStateReceiver, adapterFilter)
        }
    }

    override suspend fun pair(deviceAddress: String): TacticalResult<Unit> {
        if (!hasConnectPermission()) return TacticalResult.Failure("Missing BLUETOOTH_CONNECT permission")
        val resolvedAddress = resolveAddress(deviceAddress)
            ?: return TacticalResult.Failure("BLE address not known yet; scan for the device again")
        val device = deviceForAddress(resolvedAddress) ?: return TacticalResult.Failure("Bluetooth device not found")
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            rememberPairedPeer(deviceAddress, resolvedAddress)
            setState(resolvedAddress, BleLinkState.PAIRED)
            return TacticalResult.Success(Unit)
        }
        setState(resolvedAddress, BleLinkState.PAIRING)
        android.util.Log.d(TAG, "PAIR requested for " + resolvedAddress)
        return try {
            if (!device.createBond()) {
                setState(resolvedAddress, BleLinkState.FAILED)
                TacticalResult.Failure("Android pairing could not be started")
            } else {
                withTimeout(30_000L) {
                    while (device.bondState == BluetoothDevice.BOND_BONDING) delay(250L)
                }
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    rememberPairedPeer(deviceAddress, resolvedAddress)
                    setState(resolvedAddress, BleLinkState.PAIRED)
                    android.util.Log.d(TAG, "PAIR successful for " + resolvedAddress)
                    TacticalResult.Success(Unit)
                } else {
                    setState(resolvedAddress, BleLinkState.FAILED)
                    TacticalResult.Failure("Bluetooth pairing did not complete")
                }
            }
        } catch (_: TimeoutCancellationException) {
            setState(resolvedAddress, BleLinkState.FAILED)
            TacticalResult.Failure("Bluetooth pairing timed out")
        } catch (e: Exception) {
            setState(resolvedAddress, BleLinkState.FAILED)
            TacticalResult.Failure("Bluetooth pairing failed: ${e.message}")
        }
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
        // gattClients contains only fully initialized/ready GATT sessions.
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
            setState(resolvedAddress, state)
            if (targetGatt != null) {
                try { targetGatt.disconnect() } catch (_: Exception) {}
                try { targetGatt.close() } catch (_: Exception) {}
            }
        }

        setState(resolvedAddress, BleLinkState.CONNECTING)
        android.util.Log.d(
            TAG,
            "GATT connect requested for " + resolvedAddress +
                " (bondState=" + device.bondState + ")"
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
                        val appId = BlePeerAddressRegistry.applicationIdFor(resolvedAddress)
                        val appPaired = appId != null && pairedDeviceIds().contains(appId)
                        val state = if (appPaired) {
                            BleLinkState.DISCONNECTED
                        } else {
                            BleLinkState.NOT_PAIRED
                        }
                        rssiJobs.remove(resolvedAddress)?.cancel()
                        if (pending.remove(resolvedAddress, completion)) {
                            completion.complete(TacticalResult.Failure("GATT disconnected: status=$status"))
                        }
                        setState(resolvedAddress, state)
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
        setState(resolvedAddress, BleLinkState.DISCONNECTED)
    }

    override fun state(deviceAddress: String): Flow<BleLinkState> = stateFlow(resolveAddress(deviceAddress) ?: deviceAddress).asStateFlow()

    override fun rssi(deviceAddress: String): Flow<Int?> =
        rssiState(resolveAddress(deviceAddress) ?: deviceAddress).asStateFlow()

    override fun pairedDeviceIds(): Set<String> =
        prefs.getStringSet(PAIRED_IDS_KEY, emptySet())?.toSet() ?: emptySet()

    override suspend fun reconnectPaired(deviceAddress: String): TacticalResult<Unit> {
        val adapter = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            setState(deviceAddress, BleLinkState.DISCONNECTED)
            return TacticalResult.Failure("Bluetooth is off")
        }

        val resolvedAddress = resolveAddress(deviceAddress)
            ?: return TacticalResult.Failure("BLE address not known yet; scan for the device again")

        // Use exactly one outbound GATT initiator per paired link.
        // The other phone remains passive and uses server notifications to send
        // packets back over the same physical GATT connection.
        if (gattClients.containsKey(resolvedAddress)) {
            setState(resolvedAddress, BleLinkState.CONNECTED)
            return TacticalResult.Success(Unit)
        }

        if (!shouldInitiate(deviceAddress)) {
            if (registry.inboundDevice(resolvedAddress) != null) {
                setState(resolvedAddress, BleLinkState.CONNECTED)
                android.util.Log.d(TAG, "BLE passive link ready for " + deviceAddress)
                return TacticalResult.Success(Unit)
            }

            // Passive peers do not create a second outbound GATT session.
            // Wait for the deterministic initiator to connect to our server.
            setState(resolvedAddress, BleLinkState.PAIRED)
            android.util.Log.d(TAG, "BLE passive; waiting for initiator " + deviceAddress)
            return TacticalResult.Failure("Waiting for peer to connect")
        }

        android.util.Log.d(TAG, "BLE initiator; reconnecting to " + deviceAddress)
        val device = deviceForAddress(resolvedAddress)
            ?: return TacticalResult.Failure("Bluetooth device not found")
        android.util.Log.d(
            TAG,
            "BLE initiator resolved " + deviceAddress +
                " -> " + resolvedAddress +
                ", bondState=" + device.bondState
        )
        return connect(deviceAddress)
    }

    override suspend fun repairAndReconnect(deviceAddress: String): TacticalResult<Unit> {
        disconnect(deviceAddress)
        val device = deviceForAddress(deviceAddress) ?: return TacticalResult.Failure("Bluetooth device not found")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            val paired = pair(deviceAddress)
            if (paired is TacticalResult.Failure) return paired
        }
        return connect(deviceAddress)
    }

    override fun onInboundConnected(device: BluetoothDevice) {
        val appId = BlePeerAddressRegistry.applicationIdFor(device.address) ?: return
        if (pairedDeviceIds().contains(appId)) {
            rememberPeer(appId, device.address)
            setState(device.address, BleLinkState.CONNECTED)
            android.util.Log.d(
                TAG,
                "Inbound BLE link ready for " + appId +
                    " (bondState=" + device.bondState + ")"
            )
        }
    }

    override fun onInboundDisconnected(device: BluetoothDevice) {
        if (gattClients.containsKey(device.address)) return
        val appId = BlePeerAddressRegistry.applicationIdFor(device.address)
        setState(
            device.address,
            if (appId != null) BleLinkState.DISCONNECTED else BleLinkState.NOT_PAIRED
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

    private fun shouldInitiate(peerId: String): Boolean =
        localDeviceId.isNotBlank() &&
            peerId.isNotBlank() &&
            localDeviceId.lowercase() < peerId.lowercase()

    private fun rememberPeer(appId: String, address: String) {
        val ids = prefs.getStringSet(PAIRED_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(appId)
        prefs.edit()
            .putStringSet(PAIRED_IDS_KEY, ids)
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

    private fun rememberPairedPeer(identifier: String, address: String) {
        val appId = BlePeerAddressRegistry.applicationIdFor(address) ?: identifier
        val ids = prefs.getStringSet(PAIRED_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        ids.add(appId)
        prefs.edit()
            .putStringSet(PAIRED_IDS_KEY, ids)
            .putString(PREF_ADDRESS_PREFIX + appId, address)
            .apply()
        BlePeerAddressRegistry.remember(appId, address)
    }

    private fun forgetPairedPeer(appId: String) {
        val ids = prefs.getStringSet(PAIRED_IDS_KEY, emptySet())?.toMutableSet() ?: return
        if (ids.remove(appId)) {
            prefs.edit()
                .putStringSet(PAIRED_IDS_KEY, ids)
                .remove(PREF_ADDRESS_PREFIX + appId)
                .apply()
        }
    }

    private fun stateFlow(address: String): MutableStateFlow<BleLinkState> = states.computeIfAbsent(address) { MutableStateFlow(initialState(address)) }
    private fun setState(address: String, state: BleLinkState) { stateFlow(address).value = state }
    private fun initialState(address: String): BleLinkState = try { if (deviceForAddress(address)?.bondState == BluetoothDevice.BOND_BONDED) BleLinkState.PAIRED else BleLinkState.NOT_PAIRED } catch (_: Exception) { BleLinkState.NOT_PAIRED }
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

    companion object {
        private const val TAG = "AndroidBleConnection"
        private const val PREFS_NAME = "itantra_ble_links"
        private const val PAIRED_IDS_KEY = "paired_device_ids"
        private const val PREF_ADDRESS_PREFIX = "address_"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}