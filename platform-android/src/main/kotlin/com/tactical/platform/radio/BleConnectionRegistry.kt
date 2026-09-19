package com.tactical.platform.radio

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks connected BLE peers in both roles (this device as GATT server,
 * this device as GATT client to peers it connected out to), plus each
 * connection's negotiated MTU — needed by BleRadioTransport to fragment
 * correctly per-peer, since different peers can negotiate different MTUs.
 */
class BleConnectionRegistry {

    private val inboundDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val outboundGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val negotiatedMtu = ConcurrentHashMap<String, Int>()
    private val rssiByAddress = ConcurrentHashMap<String, Int>()
    private val transferIdCounter = AtomicInteger(0)

    // Default is the un-negotiated BLE minimum (23 total, 3 reserved for
    // ATT header) — used until onMtuChanged reports a real negotiated value.
    private val defaultUsableMtu = 20

    private val rawIncomingListeners = java.util.concurrent.CopyOnWriteArrayList<(String, ByteArray) -> Unit>()
    private val connectionListeners = java.util.concurrent.CopyOnWriteArrayList<ConnectionListener>()

    interface ConnectionListener {
        fun onInboundConnected(device: BluetoothDevice)
        fun onInboundDisconnected(device: BluetoothDevice)
    }

    fun addConnectionListener(listener: ConnectionListener) {
        connectionListeners.add(listener)
    }

    fun removeConnectionListener(listener: ConnectionListener) {
        connectionListeners.remove(listener)
    }

    fun addRawIncomingListener(listener: (String, ByteArray) -> Unit) {
        rawIncomingListeners.add(listener)
    }

    fun removeRawIncomingListener(listener: (String, ByteArray) -> Unit) {
        rawIncomingListeners.remove(listener)
    }

    /** Called by whatever holds the outbound BluetoothGattCallback when a
     *  peer notifies this device (this device acting as GATT client) of
     *  new data — not yet wired up, since outbound connection code hasn't
     *  been written yet. Every registered listener gets the raw fragment. */
    fun dispatchRawIncoming(peerAddress: String, fragment: ByteArray) {
        rawIncomingListeners.forEach { it(peerAddress, fragment) }
    }

    fun lastKnownRssi(address: String): Int? = rssiByAddress[address]

    fun registerInboundConnection(device: BluetoothDevice) {
        inboundDevices[device.address] = device
        connectionListeners.forEach { it.onInboundConnected(device) }
    }

    fun unregisterInboundConnection(device: BluetoothDevice) {
        if (inboundDevices.remove(device.address) != null) {
            connectionListeners.forEach { it.onInboundDisconnected(device) }
        }
        negotiatedMtu.remove(device.address)
    }

    fun registerOutboundConnection(gatt: BluetoothGatt) {
        outboundGatts[gatt.device.address] = gatt
    }

    fun unregisterOutboundConnection(address: String) {
        outboundGatts.remove(address)
        negotiatedMtu.remove(address)
    }

    fun onMtuNegotiated(address: String, mtu: Int) {
        // 3 bytes reserved for ATT protocol overhead per the BLE spec.
        negotiatedMtu[address] = (mtu - 3).coerceAtLeast(defaultUsableMtu)
    }

    fun usableMtuFor(address: String): Int = negotiatedMtu[address] ?: defaultUsableMtu

    fun updateRssi(address: String, rssi: Int) {
        rssiByAddress[address] = rssi
    }

    fun lastKnownRssi(device: BluetoothDevice): Int? = rssiByAddress[device.address]

    fun inboundConnectedDevices(): List<BluetoothDevice> = inboundDevices.values.toList()
    fun outboundConnectedGatts(): List<BluetoothGatt> = outboundGatts.values.toList()

    fun allConnectedAddresses(): Set<String> = inboundDevices.keys + outboundGatts.keys

    fun outboundGatt(address: String): BluetoothGatt? = outboundGatts[address]
    fun inboundDevice(address: String): BluetoothDevice? = inboundDevices[address]

    fun nextTransferId(): Int = transferIdCounter.getAndIncrement()
}