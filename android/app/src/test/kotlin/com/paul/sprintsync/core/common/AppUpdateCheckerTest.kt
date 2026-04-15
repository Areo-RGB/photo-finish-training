package com.paul.sprintsync.core.common

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppUpdateCheckerTest {

    @Test
    fun `newer integer tag returns update info`() {
        val update = extractUpdateInfoFromReleaseJson(
            releaseJson = releaseJson(
                tagName = "v12",
                assetsJson = """{"name":"app-universal-release.apk","browser_download_url":"https://example.com/app.apk"}""",
            ),
            currentVersionCode = 11,
        )

        assertNotNull(update)
        assertEquals(12, update?.versionCode)
        assertEquals("https://example.com/app.apk", update?.apkUrl)
    }

    @Test
    fun `equal or older tag returns no update`() {
        val equalVersion = extractUpdateInfoFromReleaseJson(
            releaseJson = releaseJson(
                tagName = "v12",
                assetsJson = """{"name":"app-release.apk","browser_download_url":"https://example.com/app.apk"}""",
            ),
            currentVersionCode = 12,
        )
        val olderVersion = extractUpdateInfoFromReleaseJson(
            releaseJson = releaseJson(
                tagName = "11",
                assetsJson = """{"name":"app-release.apk","browser_download_url":"https://example.com/app.apk"}""",
            ),
            currentVersionCode = 12,
        )

        assertNull(equalVersion)
        assertNull(olderVersion)
    }

    @Test
    fun `invalid tag returns no update`() {
        val update = extractUpdateInfoFromReleaseJson(
            releaseJson = releaseJson(
                tagName = "release-12",
                assetsJson = """{"name":"app-release.apk","browser_download_url":"https://example.com/app.apk"}""",
            ),
            currentVersionCode = 11,
        )

        assertNull(update)
    }

    @Test
    fun `apk selection prefers universal then release then first apk`() {
        val release = JSONObject(
            """
            {
              "assets": [
                {"name":"debug.apk","browser_download_url":"https://example.com/debug.apk"},
                {"name":"app-release-arm64.apk","browser_download_url":"https://example.com/release.apk"},
                {"name":"app-universal.apk","browser_download_url":"https://example.com/universal.apk"}
              ]
            }
            """.trimIndent(),
        )
        val selected = selectBestApkAsset(release)
        assertEquals("app-universal.apk", selected?.optString("name"))

        val noUniversal = JSONObject(
            """
            {
              "assets": [
                {"name":"debug.apk","browser_download_url":"https://example.com/debug.apk"},
                {"name":"app-release-arm64.apk","browser_download_url":"https://example.com/release.apk"}
              ]
            }
            """.trimIndent(),
        )
        val releaseSelected = selectBestApkAsset(noUniversal)
        assertEquals("app-release-arm64.apk", releaseSelected?.optString("name"))

        val fallback = JSONObject(
            """
            {
              "assets": [
                {"name":"first.apk","browser_download_url":"https://example.com/first.apk"},
                {"name":"second.apk","browser_download_url":"https://example.com/second.apk"}
              ]
            }
            """.trimIndent(),
        )
        val first = selectBestApkAsset(fallback)
        assertEquals("first.apk", first?.optString("name"))
    }

    @Test
    fun `no apk assets returns no update`() {
        val update = extractUpdateInfoFromReleaseJson(
            releaseJson = releaseJson(
                tagName = "v2",
                assetsJson = """{"name":"notes.txt","browser_download_url":"https://example.com/notes.txt"}""",
            ),
            currentVersionCode = 1,
        )

        assertNull(update)
    }

    private fun releaseJson(tagName: String, assetsJson: String): String {
        return """
            {
              "tag_name": "$tagName",
              "name": "Release $tagName",
              "body": "Notes",
              "assets": [$assetsJson]
            }
        """.trimIndent()
    }
}
