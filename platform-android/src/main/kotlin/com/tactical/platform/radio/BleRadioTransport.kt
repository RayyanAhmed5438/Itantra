package com.tactical.platform.radio

import android.Manifest
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import android.os.Build
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import java.util.UUID

class BleRadioTransport(
    private val context: Context,
    private val connectionRegistry: BleConnectionRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

) : RadioTransport {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter
    private var gattServer: BluetoothGattServer? = null
    private val reassembler = BleFragmentReassembler()

    private val sharedIncoming: Flow<RawPacket> by lazy {
        rawIncoming().shareIn(scope, SharingStarted.WhileSubscribed(replayExpirationMillis = 0), replay = 0)
    }

    override fun incoming(): Flow<RawPacket> = sharedIncoming

    private fun rawIncoming(): Flow<RawPacket> = callbackFlow {
        if (!hasBluetoothConnectPermission()) {
            // Keep the incoming transport alive while Android is showing the
            // runtime permission dialog. Closing this Flow here would make the
            // mesh receiver permanently stop until the process is restarted.
            android.util.Log.w(
                TAG,
                "BLE incoming waiting for BLUETOOTH_CONNECT permission"
            )
        }

        val adapter = try {
            bluetoothAdapter
        } catch (e: Exception) {
            android.util.Log.w(
                TAG,
                "BLE adapter unavailable; waiting for Bluetooth",
                e
            )
            null
        }

        if (adapter == null || !adapter.isEnabled) {
            android.util.Log.d(
                TAG,
                "BLE GATT server waiting for Bluetooth to turn ON"
            )
        }

        val serverCallback = object : BluetoothGattServerCallback() {
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (characteristic.uuid == PACKET_CHARACTERISTIC_UUID) {
                    android.util.Log.d(TAG, "Incoming BLE write from " + device.address + ", bytes=" + value.size)
                    reassembler.onFragmentReceived(device.address, value)?.let { complete ->
                        android.util.Log.d(TAG, "Incoming BLE packet reassembled from " + device.address + ", bytes=" + complete.size)
                        trySend(
                            RawPacket(
                                data = complete,
                                rssi = connectionRegistry.lastKnownRssi(device) ?: UNKNOWN_RSSI,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    } catch (e: SecurityException) {
                        // Permission revoked mid-session — the peer's write simply times out.
                    }
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray
            ) {
                if (descriptor.uuid == CCCD_UUID) {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            value
                        )
                    } catch (_: SecurityException) {
                        // Permission revoked mid-session.
                    }
                }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                connectionRegistry.onMtuNegotiated(device.address, mtu)
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    connectionRegistry.registerInboundConnection(device)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    connectionRegistry.unregisterInboundConnection(device)
                    reassembler.clearPeer(device.address)
                }
            }
        }

        val gattServerLock = Any()

        fun closeGattServer() {
            synchronized(gattServerLock) {
                try {
                    gattServer?.close()
                } catch (e: SecurityException) {
                    // Permission revoked or Bluetooth stack already unavailable.
                } catch (_: Exception) {
                    // Bluetooth stack may already have torn down the server.
                }
                gattServer = null
            }
        }

        fun openGattServer() {
            synchronized(gattServerLock) {
                if (!hasBluetoothConnectPermission()) {
                android.util.Log.w(TAG, "Cannot open BLE GATT server: missing BLUETOOTH_CONNECT permission")
                return
            }

            val currentAdapter = try {
                bluetoothAdapter
            } catch (_: Exception) {
                null
            }

            if (currentAdapter == null || !currentAdapter.isEnabled) {
                android.util.Log.d(TAG, "BLE GATT server waiting for Bluetooth to turn ON")
                return
            }

            closeGattServer()

            val service = BluetoothGattService(
                GATT_SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val characteristic = BluetoothGattCharacteristic(
                PACKET_CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val cccd = BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
            )
            characteristic.addDescriptor(cccd)
            service.addCharacteristic(characteristic)

            try {
                val opened = bluetoothManager.openGattServer(context, serverCallback)
                if (opened == null) {
                    android.util.Log.w(TAG, "BLE GATT server could not be opened")
                    return
                }
                if (!opened.addService(service)) {
                    android.util.Log.w(TAG, "Failed to add BLE GATT service")
                    opened.close()
                    return
                }
                gattServer = opened
                android.util.Log.d(TAG, "BLE GATT server ready")
                } catch (e: SecurityException) {
                    android.util.Log.w(TAG, "BLE GATT server unavailable: permission denied", e)
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "BLE GATT server unavailable", e)
                }
            }
        }

        val adapterReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                    BluetoothAdapter.STATE_OFF,
                    BluetoothAdapter.STATE_TURNING_OFF -> {
                        android.util.Log.d(TAG, "Bluetooth turned off; closing BLE GATT server")
                        closeGattServer()
                        connectionRegistry.inboundConnectedDevices()
                            .forEach { connectionRegistry.unregisterInboundConnection(it) }
                    }
                    BluetoothAdapter.STATE_ON -> {
                        android.util.Log.d(TAG, "Bluetooth restored; reopening BLE GATT server")
                        launch {
                            delay(750L)
                            openGattServer()
                        }
                    }
                }
            }
        }

        val adapterFilter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(adapterReceiver, adapterFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(adapterReceiver, adapterFilter)
        }

        // When Bluetooth was ON at startup, open the server immediately.
        // When it was OFF or permissions are still being granted, retry until
        // the radio/permission becomes available.
        launch {
            while (gattServer == null) {
                if (hasBluetoothConnectPermission()) {
                    openGattServer()
                }
                if (gattServer == null) delay(500L)
            }
        }

        val clientFragmentListener: (String, ByteArray) -> Unit = { address, fragment ->
            reassembler.onFragmentReceived(address, fragment)?.let { complete ->
                trySend(
                    RawPacket(
                        data = complete,
                        rssi = connectionRegistry.lastKnownRssi(address) ?: UNKNOWN_RSSI,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }
        connectionRegistry.addRawIncomingListener(clientFragmentListener)

        awaitClose {
            connectionRegistry.removeRawIncomingListener(clientFragmentListener)
            runCatching { context.unregisterReceiver(adapterReceiver) }
            closeGattServer()
        }
    }

    override suspend fun broadcast(raw: RawPacket): TacticalResult<Unit> {
        if (!hasBluetoothConnectPermission()) {
            return TacticalResult.Failure("Missing BLUETOOTH_CONNECT — request it via PermissionGateway before calling broadcast()")
        }

        // Only bonded BLE peers participate in application broadcasts.
        // Pairing is the explicit gate before a peer enters the squad.
        val inboundDevices = connectionRegistry.inboundConnectedDevices()
            .filter { it.bondState == BluetoothDevice.BOND_BONDED }
        val outboundGatts = connectionRegistry.outboundConnectedGatts()

        android.util.Log.d(TAG, "Broadcast: inbound=" + inboundDevices.size + ", outbound=" + outboundGatts.size)
        if (inboundDevices.isEmpty() && outboundGatts.isEmpty()) {
            android.util.Log.w(TAG, "Broadcast dropped: no connected peers")
            return TacticalResult.Failure("No connected peers to broadcast to")
        }

        val transferId = connectionRegistry.nextTransferId()
        var anySucceeded = false
        val failures = mutableListOf<String>()

        val server = gattServer
        val serverCharacteristic = server?.getService(GATT_SERVICE_UUID)?.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
        if (server != null && serverCharacteristic != null) {
            for (device in inboundDevices) {
                // When both sides have their own outbound GATT, prefer the
                // outbound write and do not send the same packet a second time
                // through the server notification path.
                if (connectionRegistry.outboundGatt(device.address) != null) continue

                val chunks = BleFragmenter.fragment(raw.data, transferId, connectionRegistry.usableMtuFor(device.address))
                var peerOk = true
                for (chunk in chunks) {
                    val notified = try {
                        server.notifyChanged(device, serverCharacteristic, chunk)
                    } catch (e: SecurityException) {
                        false
                    }
                    if (!notified) {
                        peerOk = false
                        break
                    }
                }
                if (peerOk) anySucceeded = true else failures.add("notify failed for ${device.address}")
                android.util.Log.d(TAG, "Notify " + device.address + " success=" + peerOk)
            }
        }

        for (gatt in outboundGatts) {
            val characteristicOut = gatt.getService(GATT_SERVICE_UUID)?.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
            if (characteristicOut == null) {
                failures.add("service not discovered on ${gatt.device.address}")
                continue
            }
            val chunks = BleFragmenter.fragment(raw.data, transferId, connectionRegistry.usableMtuFor(gatt.device.address))
            var peerOk = true
            for (chunk in chunks) {
                val written = writeChunkWithRetry(gatt, characteristicOut, chunk)
                if (!written) {
                    peerOk = false
                    break
                }
            }
            if (peerOk) anySucceeded = true else failures.add("GATT write failed for ${gatt.device.address}")
            android.util.Log.d(TAG, "Write " + gatt.device.address + " success=" + peerOk)
        }

        return if (anySucceeded) TacticalResult.Success(Unit)
        else TacticalResult.Failure("broadcast reached no peers: ${failures.joinToString("; ")}")
    }

    private fun hasBluetoothConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Legacy BLUETOOTH permission (API <31) is install-time granted, not runtime-gated.
        }

    /** Notifies using the API 33+ overload (value passed directly, avoids
     *  the deprecated characteristic.value mutation) when available. */
    private fun BluetoothGattServer.notifyChanged(
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifyCharacteristicChanged(device, characteristic, false, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            notifyCharacteristicChanged(device, characteristic, false)
        }
    } catch (e: SecurityException) {
        false
    }

    /** Tries a few times because Android may briefly reject a GATT
     * operation while the controller is busy with another packet. */
    private suspend fun writeChunkWithRetry(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        repeat(3) {
            if (gatt.writeChunk(characteristic, value)) return true
            delay(20L)
        }
        return false
    }

    /** Writes without response; the server characteristic explicitly supports
     * this mode, avoiding the response-operation bottleneck for fragments. */
    private fun BluetoothGatt.writeChunk(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            writeCharacteristic(characteristic)
        }
    } catch (_: SecurityException) {
        false
    }
    companion object {
        private const val TAG = "BleRadioTransport"
        val GATT_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private const val UNKNOWN_RSSI = 0

    }
}