package com.paul.sprintsync.core.common

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

class AppUpdateChecker(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateChecker"
    }

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val releaseNotes: String,
    )

    suspend fun checkForUpdate(
        updateCheckUrl: String,
        currentVersionCode: Int,
    ): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val json = URL(updateCheckUrl).readText()
            extractUpdateInfoFromReleaseJson(
                releaseJson = json,
                currentVersionCode = currentVersionCode,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Update check failed", e)
            null
        }
    }

    suspend fun downloadAndInstall(apkUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(context.getExternalFilesDir(null), "update.apk")
            URL(apkUrl).openStream().use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download/install failed", e)
            false
        }
    }
}

internal fun extractUpdateInfoFromReleaseJson(
    releaseJson: String,
    currentVersionCode: Int,
): AppUpdateChecker.UpdateInfo? {
    val obj = JSONObject(releaseJson)
    val tagName = obj.optString("tag_name")
    val remoteVersionCode = parseVersionCodeFromTag(tagName)
    if (remoteVersionCode == null || remoteVersionCode <= currentVersionCode) {
        return null
    }

    val apkAsset = selectBestApkAsset(obj) ?: return null
    return AppUpdateChecker.UpdateInfo(
        versionCode = remoteVersionCode,
        versionName = obj.optString("name", tagName),
        apkUrl = apkAsset.optString("browser_download_url"),
        releaseNotes = obj.optString("body", ""),
    )
}

internal fun parseVersionCodeFromTag(tagName: String?): Int? {
    if (tagName.isNullOrBlank()) return null
    return tagName.removePrefix("v").trim().toIntOrNull()
}

internal fun selectBestApkAsset(releaseObject: JSONObject): JSONObject? {
    val assets = releaseObject.optJSONArray("assets") ?: return null
    val apkAssets = (0 until assets.length())
        .map { assets.optJSONObject(it) }
        .filterNotNull()
        .filter { asset ->
            asset.optString("name").endsWith(".apk", ignoreCase = true) &&
                asset.optString("browser_download_url").isNotBlank()
        }
    if (apkAssets.isEmpty()) return null

    fun preferred(asset: JSONObject, needle: String): Boolean =
        asset.optString("name").contains(needle, ignoreCase = true)

    return apkAssets.firstOrNull { preferred(it, "universal") }
        ?: apkAssets.firstOrNull { preferred(it, "release") }
        ?: apkAssets.first()
}
