package com.paul.sprintsync.core

enum class RuntimeNetworkRole {
    NONE,
    HOST,
    CLIENT,
}

enum class RuntimeOperatingMode {
    SINGLE_DEVICE,
    NETWORK_RACE,
}

data class RuntimeDeviceConfig(
    val networkRole: RuntimeNetworkRole = RuntimeNetworkRole.NONE,
    val operatingMode: RuntimeOperatingMode = RuntimeOperatingMode.SINGLE_DEVICE,
    val profile: String = "default",
    val isControllerOnlyHost: Boolean = false,
)
