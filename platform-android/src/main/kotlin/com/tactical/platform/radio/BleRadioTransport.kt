package com.tactical.platform.radio

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
            close(SecurityException("Missing BLUETOOTH_CONNECT — request it via PermissionGateway before collecting incoming()"))
            return@callbackFlow
        }

        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth adapter unavailable or disabled"))
            return@callbackFlow
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
                    reassembler.onFragmentReceived(device.address, value)?.let { complete ->
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

        val service = BluetoothGattService(GATT_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val characteristic = BluetoothGattCharacteristic(
            PACKET_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristic)



        gattServer = try {
            bluetoothManager.openGattServer(context, serverCallback)?.also {
                it.addService(service)
            }
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        } catch (e: NullPointerException) {
            close(
                IllegalStateException(
                    "Bluetooth GATT server unavailable on this device",
                    e
                )
            )
            return@callbackFlow
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
            try {
                gattServer?.close()
            } catch (e: SecurityException) {
                // Permission revoked mid-session — nothing left to clean up safely.
            }
            gattServer = null
        }
    }

    override suspend fun broadcast(raw: RawPacket): TacticalResult<Unit> {
        if (!hasBluetoothConnectPermission()) {
            return TacticalResult.Failure("Missing BLUETOOTH_CONNECT — request it via PermissionGateway before calling broadcast()")
        }

        val inboundDevices = connectionRegistry.inboundConnectedDevices()
        val outboundGatts = connectionRegistry.outboundConnectedGatts()

        if (inboundDevices.isEmpty() && outboundGatts.isEmpty()) {
            return TacticalResult.Failure("No connected peers to broadcast to")
        }

        val transferId = connectionRegistry.nextTransferId()
        var anySucceeded = false
        val failures = mutableListOf<String>()

        val server = gattServer
        val serverCharacteristic = server?.getService(GATT_SERVICE_UUID)?.getCharacteristic(PACKET_CHARACTERISTIC_UUID)
        if (server != null && serverCharacteristic != null) {
            for (device in inboundDevices) {
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
                val written = try {
                    gatt.writeChunk(characteristicOut, chunk)
                } catch (e: SecurityException) {
                    false
                }
                if (!written) {
                    peerOk = false
                    break
                }
            }
            if (peerOk) anySucceeded = true else failures.add("GATT write failed for ${gatt.device.address}")
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

    /** Writes using the API 33+ overload for the same reason. */
    private fun BluetoothGatt.writeChunk(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            writeCharacteristic(characteristic)
        }
    } catch (e: SecurityException) {
        false
    }
    companion object {
        val GATT_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private const val UNKNOWN_RSSI = 0
    }
}