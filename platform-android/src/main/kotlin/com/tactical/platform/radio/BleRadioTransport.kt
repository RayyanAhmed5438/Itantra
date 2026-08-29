package com.tactical.platform.radio

import android.bluetooth.*
import android.content.Context
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class BleRadioTransport(
    private val context: Context,
    private val connectionRegistry: BleConnectionRegistry
) : RadioTransport {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private var gattServer: BluetoothGattServer? = null
    private val reassembler = BleFragmentReassembler()

    override fun incoming(): Flow<RawPacket> = callbackFlow {
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
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
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

        gattServer = bluetoothManager.openGattServer(context, serverCallback)?.also {
            it.addService(service)
        }

        // Fragments arriving over connections this device initiated as a
        // GATT client (outbound side) are reassembled the same way as
        // server-side writes, just fed in via this listener instead.
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
            gattServer?.close()
            gattServer = null
        }
    }

    override suspend fun broadcast(raw: RawPacket): TacticalResult<Unit> {
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
                    serverCharacteristic.value = chunk
                    if (!server.notifyCharacteristicChanged(device, serverCharacteristic, false)) {
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
                characteristicOut.value = chunk
                if (!gatt.writeCharacteristic(characteristicOut)) {
                    peerOk = false
                    break
                }
            }
            if (peerOk) anySucceeded = true else failures.add("GATT write failed for ${gatt.device.address}")
        }

        return if (anySucceeded) TacticalResult.Success(Unit)
        else TacticalResult.Failure("broadcast reached no peers: ${failures.joinToString("; ")}")
    }

    companion object {
        val GATT_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val PACKET_CHARACTERISTIC_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private const val UNKNOWN_RSSI = 0
    }
}