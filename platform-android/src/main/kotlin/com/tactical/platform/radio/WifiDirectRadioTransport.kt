package com.tactical.platform.radio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.radio.RawPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * RadioTransport over Wi-Fi Direct (P2P) sockets — the higher-throughput
 * bearer used for relay traffic and emergency audio (see WifiDirectManager
 * kdoc). Connection *formation* (peer discovery, WifiP2pManager.connect())
 * is AndroidWifiDirectManager's job; this class only takes over once a P2P
 * group exists, opening the actual data socket(s) over it.
 *
 * A Wi-Fi Direct group has exactly one group owner (GO) and one or more
 * clients. This class doesn't care which role this device ends up in — it
 * listens for WIFI_P2P_CONNECTION_CHANGED_ACTION, checks
 * WifiP2pInfo.isGroupOwner, and either runs a ServerSocket accept loop (GO)
 * or dials the GO's address (client). Wire framing is a 4-byte big-endian
 * length prefix per message, since plain TCP (unlike BLE's per-write
 * chunking) has no message boundaries of its own.
 */
class WifiDirectRadioTransport(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val wifichannel: Channel
) : RadioTransport {

    private val sockets = ConcurrentHashMap<String, Socket>()
    private val writeLocks = ConcurrentHashMap<String, Any>()

    override fun incoming(): Flow<RawPacket> = callbackFlow {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        var serverSocket: ServerSocket? = null

        fun readLoop(peerAddress: String, socket: Socket) {
            scope.launch {
                try {
                    val input = DataInputStream(socket.getInputStream())
                    while (!socket.isClosed) {
                        val length = input.readInt()
                        require(length in 1..MAX_MESSAGE_SIZE) { "implausible frame length $length from $peerAddress" }
                        val payload = ByteArray(length)
                        input.readFully(payload)
                        trySend(RawPacket(data = payload, rssi = UNKNOWN_RSSI, timestamp = System.currentTimeMillis()))
                    }
                } catch (e: IOException) {
                    // Peer dropped — normal on disconnect. broadcast() simply
                    // stops reaching this peer once its socket is removed below.
                } catch (e: IllegalArgumentException) {
                    // Malformed/corrupted frame — treat like a dropped peer, not a fatal error.
                } finally {
                    sockets.remove(peerAddress, socket)
                    writeLocks.remove(peerAddress)
                    socket.closeQuietly()
                }
            }
        }

        val connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) return

                wifiP2pManager.requestConnectionInfo(wifichannel) { info ->
                    if (info == null || !info.groupFormed) return@requestConnectionInfo

                    if (info.isGroupOwner) {
                        if (serverSocket != null) return@requestConnectionInfo
                        scope.launch {
                            try {
                                val server = ServerSocket(TRANSPORT_PORT)
                                serverSocket = server
                                while (!server.isClosed) {
                                    val client = server.accept()
                                    val peerAddress = client.inetAddress.hostAddress ?: continue
                                    sockets[peerAddress] = client
                                    writeLocks[peerAddress] = Any()
                                    readLoop(peerAddress, client)
                                }
                            } catch (e: IOException) {
                                // Server socket closed on transport teardown — expected.
                            }
                        }
                    } else {
                        val goAddress = info.groupOwnerAddress?.hostAddress ?: return@requestConnectionInfo
                        if (sockets.containsKey(goAddress)) return@requestConnectionInfo
                        scope.launch {
                            try {
                                val socket = Socket()
                                socket.connect(InetSocketAddress(goAddress, TRANSPORT_PORT), CONNECT_TIMEOUT_MS)
                                sockets[goAddress] = socket
                                writeLocks[goAddress] = Any()
                                readLoop(goAddress, socket)
                            } catch (e: IOException) {
                                // GO not listening yet, or connect failed. A future
                                // connection-changed retry (or the next broadcast()
                                // finding no socket) is how this recovers.
                            }
                        }
                    }
                }
            }
        }

        context.registerReceiver(connectionReceiver, IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))

        awaitClose {
            context.unregisterReceiver(connectionReceiver)
            sockets.values.forEach { it.closeQuietly() }
            sockets.clear()
            writeLocks.clear()
            serverSocket?.closeQuietly()
            scope.cancel()
        }
    }

    override suspend fun broadcast(raw: RawPacket): TacticalResult<Unit> = withContext(Dispatchers.IO) {
        val peers = sockets.entries.toList()
        if (peers.isEmpty()) {
            return@withContext TacticalResult.Failure("No connected Wi-Fi Direct peers to broadcast to")
        }

        var anySucceeded = false
        val failures = mutableListOf<String>()

        for ((peerAddress, socket) in peers) {
            val lock = writeLocks[peerAddress] ?: continue
            try {
                synchronized(lock) {
                    val output = DataOutputStream(socket.getOutputStream())
                    output.writeInt(raw.data.size)
                    output.write(raw.data)
                    output.flush()
                }
                anySucceeded = true
            } catch (e: IOException) {
                failures.add("write failed for $peerAddress: ${e.message}")
                sockets.remove(peerAddress, socket)
                writeLocks.remove(peerAddress)
                socket.closeQuietly()
            }
        }

        if (anySucceeded) TacticalResult.Success(Unit)
        else TacticalResult.Failure("broadcast reached no peers: ${failures.joinToString("; ")}")
    }

    private fun Socket.closeQuietly() = try { close() } catch (e: IOException) { /* already gone */ }
    private fun ServerSocket.closeQuietly() = try { close() } catch (e: IOException) { /* already gone */ }

    companion object {
        // Arbitrary unassigned port in the dynamic/private range. The GO
        // side always binds here; the client side always dials here.
        private const val TRANSPORT_PORT = 8988
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val MAX_MESSAGE_SIZE = 1024 * 64
        private const val UNKNOWN_RSSI = 0
    }
}