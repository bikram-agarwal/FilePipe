package dev.bikram.filepipe.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtainXTrackingTest {
    private fun payload(
        flavor: String,
        appId: String,
    ): JsonObject {
        val json = checkNotNull(buildObtainXTrackingJson(flavor, appId))
        return Json.parseToJsonElement(json).jsonObject
    }

    private fun apkFilterRegEx(payload: JsonObject): Regex {
        // ObtainX jsonDecode()s additionalSettings, so it must be a JSON string, not a nested object.
        val additionalSettings = payload.getValue("additionalSettings") as JsonPrimitive
        assertTrue(additionalSettings.isString)
        val settings = Json.parseToJsonElement(additionalSettings.content).jsonObject
        return Regex(settings.getValue("apkFilterRegEx").jsonPrimitive.content)
    }

    @Test
    fun githubTracksGithubReleasesWithGithubApkFilter() {
        val payload = payload("github", "dev.bikram.filepipe.gh")
        assertEquals("dev.bikram.filepipe.gh", payload.getValue("id").jsonPrimitive.content)
        assertEquals("https://github.com/bikram-agarwal/filepipe", payload.getValue("url").jsonPrimitive.content)
        assertEquals("""-github\.apk$""", apkFilterRegEx(payload).pattern)
    }

    @Test
    fun offlineTracksGithubReleasesWithOfflineApkFilter() {
        val payload = payload("offline", "dev.bikram.filepipe.offline")
        assertEquals("dev.bikram.filepipe.offline", payload.getValue("id").jsonPrimitive.content)
        assertEquals("https://github.com/bikram-agarwal/filepipe", payload.getValue("url").jsonPrimitive.content)
        assertEquals("""-offline\.apk$""", apkFilterRegEx(payload).pattern)
    }

    @Test
    fun fdroidTracksTheFdroidListingWithoutAnApkFilter() {
        val payload = payload("fdroid", "dev.bikram.filepipe.gh")
        assertEquals(
            "https://f-droid.org/packages/dev.bikram.filepipe.gh",
            payload.getValue("url").jsonPrimitive.content,
        )
        assertFalse(payload.containsKey("additionalSettings"))
    }

    @Test
    fun noPayloadOverridesTheSourceOrPinsAnApkIndex() {
        for ((flavor, appId) in listOf("github" to "a.gh", "offline" to "a.offline", "fdroid" to "a.gh")) {
            val payload = payload(flavor, appId)
            assertFalse(payload.containsKey("overrideSource"))
            assertFalse(payload.containsKey("preferredApkIndex"))
        }
    }

    @Test
    fun playStoreBuildsAreNotOfferedTracking() {
        assertNull(buildObtainXTrackingJson("playstore", "dev.bikram.filepipe"))
    }

    @Test
    fun releaseApplicationIdDropsOnlyTheDevReleaseSuffix() {
        assertEquals("dev.bikram.filepipe.gh", releaseApplicationId("dev.bikram.filepipe.gh.dev"))
        assertEquals("dev.bikram.filepipe.offline", releaseApplicationId("dev.bikram.filepipe.offline"))
        assertEquals("dev.bikram.filepipe.gh", releaseApplicationId("dev.bikram.filepipe.gh"))
    }

    @Test
    fun apkFiltersSelectOnlyTheMatchingFlavorAsset() {
        // ObtainX keeps an asset when the filter matches its name *or* its download URL.
        val downloadBase = "https://github.com/bikram-agarwal/filepipe/releases/download/v3.12.0/"
        val assets = listOf("filepipe-v3.12.0-fdroid.apk", "filepipe-v3.12.0-github.apk", "filepipe-v3.12.0-offline.apk")
        val cases =
            mapOf(
                "github" to "filepipe-v3.12.0-github.apk",
                "offline" to "filepipe-v3.12.0-offline.apk",
            )
        for ((flavor, expected) in cases) {
            val filter = apkFilterRegEx(payload(flavor, "dev.bikram.filepipe.$flavor"))
            val kept = assets.filter { name -> filter.containsMatchIn(name) || filter.containsMatchIn(downloadBase + name) }
            assertEquals(listOf(expected), kept)
        }
    }
}
