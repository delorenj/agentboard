package com.zellij.keyboard.input

import com.zellij.keyboard.core.ZellijRemoteAction
import java.net.HttpURLConnection
import java.net.URL

internal interface ZellijBridgeClient {
    fun drive(action: ZellijRemoteAction): Boolean
}

/** Synchronous network seam; callers must invoke it from a background executor. */
internal class HttpZellijBridgeClient(
    baseUrl: String,
    private val token: String,
) : ZellijBridgeClient {
    private val actionUrl = URL("${baseUrl.trimEnd('/')}/v1/action")

    val isConfigured: Boolean
        get() = token.isNotBlank()

    override fun drive(action: ZellijRemoteAction): Boolean {
        if (!isConfigured) {
            return false
        }

        val connection = actionUrl.openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("{\"action\":\"${action.wireName}\"}")
            }
            connection.responseCode in 200..299
        } catch (_: RuntimeException) {
            false
        } catch (_: java.io.IOException) {
            false
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val READ_TIMEOUT_MILLIS = 6_000
    }
}
