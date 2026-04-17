package com.paul.sprintsync.core.services

import android.os.Handler
import android.os.Looper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class TcpConnectionsManager(
    private val hostPort: Int,
    private val nsdDiscovery: NsdServiceDiscovery? = null,
    private val resolveGatewayIp: (() -> String?)? = null,
    private val fallbackHostIp: String? = null,
) : SessionConnectionsManager {
    companion object {
        private const val FRAME_KIND_MESSAGE: Byte = 1
        private const val DISCOVERY_FALLBACK_TIMEOUT_MS = 1_500L
    }

    private val ioExecutor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectedSockets = ConcurrentHashMap<String, Socket>()
    private val endpointNamesById = ConcurrentHashMap<String, String>()
    private val discoveryRunning = AtomicBoolean(false)
    private val discoveryFallbackConsumed = AtomicBoolean(false)
    private var discoveryFallbackRunnable: Runnable? = null

    @Volatile
    private var eventListener: ((NearbyEvent) -> Unit)? = null

    @Volatile
    private var activeRole: NearbyRole = NearbyRole.NONE

    @Volatile
    private var activeStrategy: NearbyTransportStrategy = NearbyTransportStrategy.POINT_TO_POINT

    @Volatile
    private var serverSocket: ServerSocket? = null

    override fun setEventListener(listener: ((NearbyEvent) -> Unit)?) {
        eventListener = listener
    }

    override fun currentRole(): NearbyRole = activeRole

    override fun currentStrategy(): NearbyTransportStrategy = activeStrategy

    override fun connectedEndpoints(): Set<String> = connectedSockets.keys.toSet()

    override fun startHosting(
        serviceId: String,
        endpointName: String,
        strategy: NearbyTransportStrategy,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        stopAll()
        activeRole = NearbyRole.HOST
        activeStrategy = strategy
        try {
            val socket = ServerSocket(hostPort)
            serverSocket = socket
            nsdDiscovery?.registerService(endpointName, hostPort) { result ->
                result.exceptionOrNull()?.let { error ->
                    emitError("NSD registration failed: ${error.localizedMessage ?: "unknown"}")
                }
            }
            ioExecutor.execute {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        val endpointId = endpointIdForSocket(client)
                        if (
                            activeStrategy == NearbyTransportStrategy.POINT_TO_POINT &&
                            connectedSockets.keys.any { connectedEndpointId -> connectedEndpointId != endpointId }
                        ) {
                            client.close()
                            emitEvent(
                                NearbyEvent.ConnectionResult(
                                    endpointId = endpointId,
                                    endpointName = endpointId,
                                    connected = false,
                                    statusCode = -2,
                                    statusMessage = "point_to_point busy",
                                ),
                            )
                            continue
                        }
                        val previous = connectedSockets.put(endpointId, client)
                        if (previous != null && previous !== client) {
                            runCatching { previous.close() }
                            emitEvent(NearbyEvent.EndpointDisconnected(endpointId = endpointId))
                        }
                        endpointNamesById[endpointId] = endpointId
                        emitEvent(
                            NearbyEvent.ConnectionResult(
                                endpointId = endpointId,
                                endpointName = endpointId,
                                connected = true,
                                statusCode = 0,
                                statusMessage = "connected",
                            ),
                        )
                        startReaderLoop(endpointId, client)
                    } catch (_: SocketException) {
                        break
                    } catch (error: Throwable) {
                        emitError("accept failed: ${error.localizedMessage ?: "unknown"}")
                    }
                }
            }
            onComplete(Result.success(Unit))
        } catch (error: Throwable) {
            emitError("startHosting failed: ${error.localizedMessage ?: "unknown"}")
            onComplete(Result.failure(error))
        }
    }

    override fun startDiscovery(serviceId: String, strategy: NearbyTransportStrategy, onComplete: (Result<Unit>) -> Unit) {
        connectedSockets.clear()
        endpointNamesById.clear()
        activeRole = NearbyRole.CLIENT
        activeStrategy = strategy
        discoveryRunning.set(true)
        discoveryFallbackConsumed.set(false)
        scheduleGatewayFallback(serviceId)
        if (nsdDiscovery != null) {
            nsdDiscovery.discoverServices(
                onServiceFound = { hostAddress, _, serviceName ->
                    try {
                        if (!discoveryRunning.get()) {
                            return@discoverServices
                        }
                        discoveryFallbackConsumed.set(true)
                        cancelDiscoveryFallback()
                        emitEvent(
                            NearbyEvent.EndpointFound(
                                endpointId = hostAddress,
                                endpointName = serviceName,
                                serviceId = serviceId,
                            ),
                        )
                    } catch (error: Throwable) {
                        emitError("NSD discovery callback failed: ${error.localizedMessage ?: "unknown"}")
                    }
                },
                onError = { errorMsg ->
                    emitError("NSD discovery failed: $errorMsg")
                    emitGatewayFallbackEndpoint(serviceId)
                },
            )
        } else {
            emitGatewayFallbackEndpoint(serviceId)
        }
        onComplete(Result.success(Unit))
    }

    private fun scheduleGatewayFallback(serviceId: String) {
        cancelDiscoveryFallback()
        val fallbackRunnable = Runnable {
            emitGatewayFallbackEndpoint(serviceId)
        }
        discoveryFallbackRunnable = fallbackRunnable
        mainHandler.postDelayed(fallbackRunnable, DISCOVERY_FALLBACK_TIMEOUT_MS)
    }

    private fun cancelDiscoveryFallback() {
        val runnable = discoveryFallbackRunnable ?: return
        mainHandler.removeCallbacks(runnable)
        discoveryFallbackRunnable = null
    }

    private fun emitGatewayFallbackEndpoint(serviceId: String) {
        if (!discoveryRunning.get()) {
            return
        }
        if (!discoveryFallbackConsumed.compareAndSet(false, true)) {
            return
        }
        cancelDiscoveryFallback()
        val gatewayIp = resolveGatewayIp?.invoke()?.takeIf { it.isNotBlank() }
        val resolvedIp = gatewayIp ?: fallbackHostIp?.takeIf { it.isNotBlank() }
        if (resolvedIp != null) {
            emitEvent(
                NearbyEvent.EndpointFound(
                    endpointId = resolvedIp,
                    endpointName = resolvedIp,
                    serviceId = serviceId,
                ),
            )
        } else {
            emitEvent(NearbyEvent.Error("No host IP: NSD unavailable, DHCP gateway null, no fallback"))
        }
    }

    override fun requestConnection(endpointId: String, endpointName: String, onComplete: (Result<Unit>) -> Unit) {
        if (activeRole != NearbyRole.CLIENT) {
            onComplete(Result.failure(IllegalStateException("requestConnection ignored: not in client mode.")))
            return
        }
        val targetHost = endpointId.substringBefore(":").ifBlank {
            resolveGatewayIp?.invoke() ?: fallbackHostIp ?: "127.0.0.1"
        }
        ioExecutor.execute {
            try {
                val socket = Socket(targetHost, hostPort)
                val connectedId = endpointIdForSocket(socket)
                val previous = connectedSockets.put(connectedId, socket)
                if (previous != null && previous !== socket) {
                    runCatching { previous.close() }
                    emitEvent(NearbyEvent.EndpointDisconnected(endpointId = connectedId))
                }
                endpointNamesById[connectedId] = endpointId
                discoveryRunning.set(false)
                cancelDiscoveryFallback()
                emitEvent(
                    NearbyEvent.ConnectionResult(
                        endpointId = connectedId,
                        endpointName = endpointId,
                        connected = true,
                        statusCode = 0,
                        statusMessage = "connected",
                    ),
                )
                startReaderLoop(connectedId, socket)
                onComplete(Result.success(Unit))
            } catch (error: Throwable) {
                emitEvent(
                    NearbyEvent.ConnectionResult(
                        endpointId = endpointId,
                        endpointName = endpointId,
                        connected = false,
                        statusCode = -1,
                        statusMessage = error.localizedMessage ?: "connect failed",
                    ),
                )
                onComplete(Result.failure(error))
            }
        }
    }

    override fun sendMessage(endpointId: String, messageJson: String, onComplete: (Result<Unit>) -> Unit) {
        val payload = messageJson.toByteArray(StandardCharsets.UTF_8)
        sendFrame(endpointId, FRAME_KIND_MESSAGE, payload, onComplete)
    }

    private fun sendFrame(endpointId: String, kind: Byte, payloadBytes: ByteArray, onComplete: (Result<Unit>) -> Unit) {
        val socket = connectedSockets[endpointId]
        if (socket == null) {
            onComplete(Result.failure(IllegalStateException("endpoint not connected ($endpointId)")))
            return
        }
        ioExecutor.execute {
            try {
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                synchronized(socket) {
                    out.writeByte(kind.toInt())
                    out.writeInt(payloadBytes.size)
                    out.write(payloadBytes)
                    out.flush()
                }
                onComplete(Result.success(Unit))
            } catch (error: Throwable) {
                onComplete(Result.failure(error))
                handleDisconnect(endpointId, socket)
            }
        }
    }

    private fun startReaderLoop(endpointId: String, socket: Socket) {
        ioExecutor.execute {
            try {
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                while (!socket.isClosed) {
                    val frameKind = input.readByte()
                    val frameLength = input.readInt()
                    if (frameLength <= 0 || frameLength > 1_048_576) {
                        throw IllegalStateException("invalid frame size: $frameLength")
                    }
                    val payload = ByteArray(frameLength)
                    input.readFully(payload)
                    when (frameKind) {
                        FRAME_KIND_MESSAGE -> {
                            emitEvent(
                                NearbyEvent.PayloadReceived(
                                    endpointId = endpointId,
                                    message = String(payload, StandardCharsets.UTF_8),
                                ),
                            )
                        }
                        else -> emitError("frame dropped: unsupported kind=$frameKind")
                    }
                }
            } catch (_: EOFException) {
                handleDisconnect(endpointId, socket)
            } catch (_: SocketException) {
                handleDisconnect(endpointId, socket)
            } catch (error: Throwable) {
                emitError("read failed: ${error.localizedMessage ?: "unknown"}")
                handleDisconnect(endpointId, socket)
            }
        }
    }

    override fun stopAll() {
        discoveryRunning.set(false)
        cancelDiscoveryFallback()
        nsdDiscovery?.stopAll()
        serverSocket?.close()
        serverSocket = null
        connectedSockets.forEach { (_, socket) ->
            runCatching { socket.close() }
        }
        connectedSockets.clear()
        endpointNamesById.clear()
        activeRole = NearbyRole.NONE
        activeStrategy = NearbyTransportStrategy.POINT_TO_POINT
    }

    private fun handleDisconnect(endpointId: String, socket: Socket) {
        val removed = connectedSockets.remove(endpointId, socket)
        if (!removed) {
            return
        }
        runCatching { socket.close() }
        endpointNamesById.remove(endpointId)
        emitEvent(NearbyEvent.EndpointDisconnected(endpointId = endpointId))
    }

    private fun endpointIdForSocket(socket: Socket): String {
        return tcpEndpointIdFromHostAddress(socket.inetAddress?.hostAddress)
    }

    private fun emitError(message: String) {
        emitEvent(NearbyEvent.Error(message))
    }

    private fun emitEvent(event: NearbyEvent) {
        val listener = eventListener ?: return
        mainHandler.post { listener(event) }
    }
}

internal fun tcpEndpointIdFromHostAddress(hostAddress: String?): String {
    return hostAddress?.trim()?.takeIf { value -> value.isNotEmpty() } ?: "unknown"
}
