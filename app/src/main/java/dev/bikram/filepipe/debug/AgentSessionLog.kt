package dev.bikram.filepipe.debug

import android.content.Context
import org.json.JSONObject

/**
 * Debug-mode NDJSON line (session c5e21d). Written under app external files dir as `debug-c5e21d.log`.
 * Pull with: adb shell run-as <package> cat files/../files/debug-c5e21d.log
 * or from external: Android/data/<package>/files/debug-c5e21d.log
 */
// #region agent log
object AgentSessionLog {
    private const val FILE_NAME = "debug-c5e21d.log"

    fun append(
        context: Context,
        location: String,
        message: String,
        hypothesisId: String,
        data: Map<String, Any?> = emptyMap()
    ) {
        try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val dataJson = JSONObject()
            data.forEach { (key, value) ->
                dataJson.put(key, value ?: JSONObject.NULL)
            }
            val line = JSONObject().apply {
                put("sessionId", "c5e21d")
                put("timestamp", System.currentTimeMillis())
                put("location", location)
                put("message", message)
                put("hypothesisId", hypothesisId)
                put("data", dataJson)
            }.toString()
            java.io.File(dir, FILE_NAME).appendText(line + "\n")
        } catch (_: Exception) {
        }
    }
}
// #endregion
