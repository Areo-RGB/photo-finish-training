package com.paul.sprintsync.core

import android.os.Build

internal object DeviceDetector {
    private const val XIAOMI_PAD_MODEL = "2410CRP4CG"
    private const val ONEPLUS_MODEL = "CPH2399"

    fun detectCurrentDevice(): RuntimeDeviceConfig {
        return detect(
            model = Build.MODEL?.trim().orEmpty(),
            manufacturer = Build.MANUFACTURER?.trim().orEmpty(),
        )
    }

    fun detect(model: String, manufacturer: String): RuntimeDeviceConfig {
        val normalizedModel = model.trim().uppercase()
        val normalizedManufacturer = manufacturer.trim().uppercase()
        return when {
            normalizedModel == XIAOMI_PAD_MODEL &&
                normalizedManufacturer.contains("XIAOMI") -> RuntimeDeviceConfig(
                networkRole = RuntimeNetworkRole.HOST,
                operatingMode = RuntimeOperatingMode.SINGLE_DEVICE,
                profile = "host_xiaomi",
                isControllerOnlyHost = true,
            )
            normalizedModel == ONEPLUS_MODEL &&
                normalizedManufacturer.contains("ONEPLUS") -> RuntimeDeviceConfig(
                networkRole = RuntimeNetworkRole.CLIENT,
                operatingMode = RuntimeOperatingMode.SINGLE_DEVICE,
                profile = "default",
                isControllerOnlyHost = false,
            )
            else -> RuntimeDeviceConfig(
                networkRole = RuntimeNetworkRole.NONE,
                operatingMode = RuntimeOperatingMode.SINGLE_DEVICE,
                profile = "default",
                isControllerOnlyHost = false,
            )
        }
    }
}
