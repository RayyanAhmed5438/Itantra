@file:Suppress("DEPRECATION")

package com.tactical.platform.ble

import android.Manifest
import android.annotation.SuppressLint
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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
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
    private val registry: BleConnectionRegistry
) : BleConnectionManager {

    private val states = ConcurrentHashMap<String, MutableStateFlow<BleLinkState>>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<TacticalResult<Unit>>>()
    private val gattClients = ConcurrentHashMap<String, BluetoothGatt>()
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
                BluetoothDevice.BOND_BONDED -> setState(device.address, BleLinkState.PAIRED)
                BluetoothDevice.BOND_NONE -> setState(device.address, BleLinkState.NOT_PAIRED)
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION") context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
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
        val resolvedAddress = resolveAddress(deviceAddress)
            ?: return TacticalResult.Failure("BLE address not known yet; scan for the device again")
        val device = deviceForAddress(resolvedAddress) ?: return TacticalResult.Failure("Bluetooth device not found")
        if (device.bondState != BluetoothDevice.BOND_BONDED) return TacticalResult.Failure("Pair the device before connecting")
        if (gattClients.containsKey(resolvedAddress)) {
            setState(resolvedAddress, BleLinkState.CONNECTED)
            return TacticalResult.Success(Unit)
        }
        setState(resolvedAddress, BleLinkState.CONNECTING)
        val completion = CompletableDeferred<TacticalResult<Unit>>()
        pending[resolvedAddress] = completion
        return try {
            val callback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        gattClients[resolvedAddress] = gatt
                        registry.registerOutboundConnection(gatt)
                        setState(resolvedAddress, BleLinkState.CONNECTING)
                        try { gatt.discoverServices() } catch (_: Exception) {
                            pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("GATT service discovery could not start"))
                            try { gatt.disconnect() } catch (_: Exception) {}
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        gattClients.remove(resolvedAddress, gatt)
                        registry.unregisterOutboundConnection(resolvedAddress)
                        setState(resolvedAddress, if (device.bondState == BluetoothDevice.BOND_BONDED) BleLinkState.DISCONNECTED else BleLinkState.NOT_PAIRED)
                        pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("GATT disconnected: status=$status"))
                        try { gatt.close() } catch (_: Exception) {}
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("GATT service discovery failed: $status"))
                        setState(resolvedAddress, BleLinkState.FAILED)
                        try { gatt.disconnect() } catch (_: Exception) {}
                        return
                    }
                    val service = gatt.getService(BleRadioTransport.GATT_SERVICE_UUID)
                    val characteristic = service?.getCharacteristic(BleRadioTransport.PACKET_CHARACTERISTIC_UUID)
                    if (characteristic == null) {
                        pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("iTantra GATT service not found"))
                        setState(resolvedAddress, BleLinkState.FAILED)
                        try { gatt.disconnect() } catch (_: Exception) {}
                        return
                    }
                    if (!gatt.setCharacteristicNotification(characteristic, true)) {
                        pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("Could not enable notifications"))
                        return
                    }
                    val descriptor = characteristic.getDescriptor(CCCD_UUID)
                    if (descriptor == null) {
                        pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("CCCD descriptor missing"))
                        return
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val rc = gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            if (rc != BluetoothStatusCodes.SUCCESS) pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("Notification descriptor write failed: $rc"))
                        } else {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            if (!gatt.writeDescriptor(descriptor)) pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("Notification descriptor write failed"))
                        }
                    } catch (e: Exception) {
                        pending.remove(resolvedAddress)?.complete(TacticalResult.Failure("Notification setup failed: ${e.message}"))
                    }
                }

                override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    if (descriptor.uuid == CCCD_UUID) pending.remove(resolvedAddress)?.complete(if (status == BluetoothGatt.GATT_SUCCESS) TacticalResult.Success(Unit) else TacticalResult.Failure("CCCD write status=$status"))
                }

                @Deprecated("Use the value overload on API 33+.")
                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    if (characteristic.uuid == BleRadioTransport.PACKET_CHARACTERISTIC_UUID) registry.dispatchRawIncoming(resolvedAddress, characteristic.value)
                }

                override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    if (characteristic.uuid == BleRadioTransport.PACKET_CHARACTERISTIC_UUID) registry.dispatchRawIncoming(resolvedAddress, value)
                }

                override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) registry.onMtuNegotiated(resolvedAddress, mtu)
                }
            }
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) else {
                device.connectGatt(context, false, callback)
            }
            try {
                withTimeout(20_000L) { completion.await() }
            } catch (_: TimeoutCancellationException) {
                pending.remove(resolvedAddress)
                try { gatt.disconnect() } catch (_: Exception) {}
                try { gatt.close() } catch (_: Exception) {}
                setState(resolvedAddress, BleLinkState.FAILED)
                TacticalResult.Failure("GATT connection timed out")
            }
        } catch (e: Exception) {
            pending.remove(resolvedAddress)
            setState(resolvedAddress, BleLinkState.FAILED)
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

    override fun pairedDeviceIds(): Set<String> =
        prefs.getStringSet(PAIRED_IDS_KEY, emptySet())?.toSet() ?: emptySet()

    override suspend fun reconnectPaired(deviceAddress: String): TacticalResult<Unit> {
        return if (deviceForAddress(deviceAddress)?.bondState == BluetoothDevice.BOND_BONDED) connect(deviceAddress) else TacticalResult.Failure("Peer is not paired")
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
        private const val PREFS_NAME = "itantra_ble_links"
        private const val PAIRED_IDS_KEY = "paired_device_ids"
        private const val PREF_ADDRESS_PREFIX = "address_"
        private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}