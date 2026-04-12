package com.paul.sprintsync.features.race_session

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

enum class SessionStage {
    SETUP,
    MONITORING,
}

enum class SessionOperatingMode {
    SINGLE_DEVICE,
    DISPLAY_HOST,
}

enum class SessionNetworkRole {
    NONE,
    HOST,
    CLIENT,
}

enum class SessionDeviceRole {
    UNASSIGNED,
    CONTROLLER,
    DISPLAY,
}

enum class SessionCameraFacing {
    REAR,
    FRONT,
}

data class SessionDevice(
    val id: String,
    val name: String,
    val role: SessionDeviceRole = SessionDeviceRole.UNASSIGNED,
    val cameraFacing: SessionCameraFacing = SessionCameraFacing.REAR,
    val isLocal: Boolean,
) {
    fun toJsonObject(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("role", role.name.lowercase())
            .put("cameraFacing", cameraFacing.name.lowercase())
            .put("isLocal", isLocal)
    }

    companion object {
        fun fromJsonObject(decoded: JSONObject): SessionDevice? {
            val id = decoded.optString("id", "").trim()
            val name = decoded.optString("name", "").trim()
            if (id.isEmpty() || name.isEmpty()) {
                return null
            }
            return SessionDevice(
                id = id,
                name = name,
                role = sessionDeviceRoleFromName(decoded.readOptionalString("role"))
                    ?: SessionDeviceRole.UNASSIGNED,
                cameraFacing = sessionCameraFacingFromName(decoded.readOptionalString("cameraFacing"))
                    ?: SessionCameraFacing.REAR,
                isLocal = decoded.optBoolean("isLocal", false),
            )
        }
    }
}

data class SessionLapResultMessage(
    val senderDeviceName: String,
    val elapsedNanos: Long,
) {
    fun toJsonString(): String {
        return JSONObject()
            .put("type", TYPE)
            .put("senderDeviceName", senderDeviceName)
            .put("elapsedNanos", elapsedNanos)
            .toString()
    }

    companion object {
        const val TYPE = "lap_result"

        fun tryParse(raw: String): SessionLapResultMessage? {
            val decoded = try {
                JSONObject(raw)
            } catch (_: JSONException) {
                return null
            }
            if (decoded.optString("type") != TYPE) {
                return null
            }
            val senderDeviceName = decoded.optString("senderDeviceName", "").trim()
            val elapsedNanos = decoded.optLong("elapsedNanos", Long.MIN_VALUE)
            if (senderDeviceName.isEmpty() || elapsedNanos <= 0L || elapsedNanos == Long.MIN_VALUE) {
                return null
            }
            return SessionLapResultMessage(
                senderDeviceName = senderDeviceName,
                elapsedNanos = elapsedNanos,
            )
        }
    }
}

enum class SessionControlAction {
    RESET_TIMER,
    SET_DISPLAY_LIMIT,
    SET_MOTION_SENSITIVITY,
    SET_AUTO_READY_DELAY,
}

data class SessionControlCommandMessage(
    val action: SessionControlAction,
    val targetEndpointId: String,
    val senderDeviceName: String,
    val limitMillis: Long?,
    val sensitivityPercent: Int?,
    val autoReadyDelaySeconds: Int? = null,
) {
    fun toJsonString(): String {
        return JSONObject()
            .put("type", TYPE)
            .put("action", action.name.lowercase())
            .put("targetEndpointId", targetEndpointId)
            .put("senderDeviceName", senderDeviceName)
            .put("limitMillis", limitMillis ?: JSONObject.NULL)
            .put("sensitivityPercent", sensitivityPercent ?: JSONObject.NULL)
            .put("autoReadyDelaySeconds", autoReadyDelaySeconds ?: JSONObject.NULL)
            .toString()
    }

    companion object {
        const val TYPE = "control_command"

        fun tryParse(raw: String): SessionControlCommandMessage? {
            val decoded = try {
                JSONObject(raw)
            } catch (_: JSONException) {
                return null
            }
            if (decoded.optString("type") != TYPE) {
                return null
            }
            val action = sessionControlActionFromName(decoded.readOptionalString("action")) ?: return null
            val targetEndpointId = decoded.optString("targetEndpointId", "").trim()
            val senderDeviceName = decoded.optString("senderDeviceName", "").trim()
            val limitMillis = decoded.readOptionalLong("limitMillis")
                ?: decoded.readOptionalLong("limitSeconds")?.times(1_000L)
            val sensitivityPercent = decoded.readOptionalInt("sensitivityPercent")
            val autoReadyDelaySeconds = decoded.readOptionalInt("autoReadyDelaySeconds")
            if (targetEndpointId.isEmpty() || senderDeviceName.isEmpty()) {
                return null
            }
            if (action == SessionControlAction.SET_DISPLAY_LIMIT && (limitMillis == null || limitMillis <= 0L)) {
                return null
            }
            if (
                action == SessionControlAction.SET_MOTION_SENSITIVITY &&
                (sensitivityPercent == null || sensitivityPercent !in 0..100)
            ) {
                return null
            }
            if (
                action == SessionControlAction.SET_AUTO_READY_DELAY &&
                (autoReadyDelaySeconds != null && autoReadyDelaySeconds !in 1..5)
            ) {
                return null
            }
            return SessionControlCommandMessage(
                action = action,
                targetEndpointId = targetEndpointId,
                senderDeviceName = senderDeviceName,
                limitMillis = limitMillis,
                sensitivityPercent = sensitivityPercent,
                autoReadyDelaySeconds = autoReadyDelaySeconds,
            )
        }
    }
}

data class SessionControllerTarget(
    val endpointId: String,
    val deviceName: String,
)

data class SessionControllerTargetsMessage(
    val senderDeviceName: String,
    val targets: List<SessionControllerTarget>,
) {
    fun toJsonString(): String {
        val targetsJson = JSONArray()
        targets.forEach { target ->
            targetsJson.put(
                JSONObject()
                    .put("endpointId", target.endpointId)
                    .put("deviceName", target.deviceName),
            )
        }
        return JSONObject()
            .put("type", TYPE)
            .put("senderDeviceName", senderDeviceName)
            .put("targets", targetsJson)
            .toString()
    }

    companion object {
        const val TYPE = "controller_targets"

        fun tryParse(raw: String): SessionControllerTargetsMessage? {
            val decoded = try {
                JSONObject(raw)
            } catch (_: JSONException) {
                return null
            }
            if (decoded.optString("type") != TYPE) {
                return null
            }
            val senderDeviceName = decoded.optString("senderDeviceName", "").trim()
            if (senderDeviceName.isEmpty()) {
                return null
            }
            val rawTargets = decoded.optJSONArray("targets") ?: return null
            val targets = mutableListOf<SessionControllerTarget>()
            for (index in 0 until rawTargets.length()) {
                val item = rawTargets.optJSONObject(index) ?: continue
                val endpointId = item.optString("endpointId", "").trim()
                val deviceName = item.optString("deviceName", "").trim()
                if (endpointId.isEmpty() || deviceName.isEmpty()) {
                    continue
                }
                targets += SessionControllerTarget(
                    endpointId = endpointId,
                    deviceName = deviceName,
                )
            }
            return SessionControllerTargetsMessage(
                senderDeviceName = senderDeviceName,
                targets = targets,
            )
        }
    }
}

data class SessionControllerIdentityMessage(
    val senderDeviceName: String,
) {
    fun toJsonString(): String {
        return JSONObject()
            .put("type", TYPE)
            .put("senderDeviceName", senderDeviceName)
            .toString()
    }

    companion object {
        const val TYPE = "controller_identity"

        fun tryParse(raw: String): SessionControllerIdentityMessage? {
            val decoded = try {
                JSONObject(raw)
            } catch (_: JSONException) {
                return null
            }
            if (decoded.optString("type") != TYPE) {
                return null
            }
            val senderDeviceName = decoded.optString("senderDeviceName", "").trim()
            if (senderDeviceName.isEmpty()) {
                return null
            }
            return SessionControllerIdentityMessage(senderDeviceName = senderDeviceName)
        }
    }
}

fun sessionStageFromName(name: String?): SessionStage? {
    if (name == null) {
        return null
    }
    return SessionStage.values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

fun sessionDeviceRoleFromName(name: String?): SessionDeviceRole? {
    if (name == null) {
        return null
    }
    return SessionDeviceRole.values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

fun sessionCameraFacingFromName(name: String?): SessionCameraFacing? {
    if (name == null) {
        return null
    }
    return SessionCameraFacing.values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

fun sessionDeviceRoleLabel(role: SessionDeviceRole): String {
    return when (role) {
        SessionDeviceRole.UNASSIGNED -> "Unassigned"
        SessionDeviceRole.CONTROLLER -> "Controller"
        SessionDeviceRole.DISPLAY -> "Display"
    }
}

fun sessionControlActionFromName(name: String?): SessionControlAction? {
    if (name == null) {
        return null
    }
    return SessionControlAction.values().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}

fun sessionCameraFacingLabel(facing: SessionCameraFacing): String {
    return when (facing) {
        SessionCameraFacing.REAR -> "Rear"
        SessionCameraFacing.FRONT -> "Front"
    }
}

private fun JSONObject.readOptionalLong(key: String): Long? {
    if (!has(key) || isNull(key)) {
        return null
    }
    val value = optLong(key, Long.MIN_VALUE)
    return value.takeIf { it != Long.MIN_VALUE }
}

private fun JSONObject.readOptionalString(key: String): String? {
    if (!has(key) || isNull(key)) {
        return null
    }
    return optString(key, "").ifBlank { null }
}

private fun JSONObject.readOptionalInt(key: String): Int? {
    if (!has(key) || isNull(key)) {
        return null
    }
    val value = optInt(key, Int.MIN_VALUE)
    return value.takeIf { it != Int.MIN_VALUE }
}