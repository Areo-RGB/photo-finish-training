package com.paul.sprintsync.core.services

interface SessionConnectionsManager {
    fun setEventListener(listener: ((NearbyEvent) -> Unit)?)

    fun currentRole(): NearbyRole

    fun currentStrategy(): NearbyTransportStrategy

    fun connectedEndpoints(): Set<String>

    fun startHosting(
        serviceId: String,
        endpointName: String,
        strategy: NearbyTransportStrategy,
        onComplete: (Result<Unit>) -> Unit,
    )

    fun startDiscovery(serviceId: String, strategy: NearbyTransportStrategy, onComplete: (Result<Unit>) -> Unit)

    fun requestConnection(endpointId: String, endpointName: String, onComplete: (Result<Unit>) -> Unit)

    fun sendMessage(endpointId: String, messageJson: String, onComplete: (Result<Unit>) -> Unit)

    fun stopAll()
}
