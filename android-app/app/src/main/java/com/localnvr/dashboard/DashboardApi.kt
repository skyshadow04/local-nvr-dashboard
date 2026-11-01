package com.localnvr.dashboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object DashboardApi {
    fun normalizeServerUrl(value: String): String {
        val trimmed = value.trim().trimEnd('/')
        require(trimmed.isNotEmpty()) { "Enter the dashboard address." }
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val uri = URI(withScheme)
        require(uri.scheme == "http" || uri.scheme == "https") {
            "The dashboard address must use HTTP or HTTPS."
        }
        require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "Enter a host address only, such as http://100.x.y.z:3001."
        }
        require(uri.path.isNullOrEmpty() || uri.path == "/") {
            "Enter the dashboard root address, without an extra path."
        }
        return "${uri.scheme}://${uri.rawAuthority}"
    }

    suspend fun getCameras(serverUrl: String): List<Camera> = withContext(Dispatchers.IO) {
        val response = request("$serverUrl/api/stream")
        val cameras = JSONObject(response).optJSONArray("cameras")
            ?: return@withContext emptyList()
        List(cameras.length()) { index -> Camera.fromJson(cameras.getJSONObject(index)) }
    }

    suspend fun postOffer(serverUrl: String, streamId: String, sdp: String): String =
        withContext(Dispatchers.IO) {
            val encodedId = java.net.URLEncoder.encode(streamId, Charsets.UTF_8.name())
                .replace("+", "%20")
            request(
                url = "$serverUrl/api/whep/$encodedId",
                method = "POST",
                contentType = "application/sdp",
                body = sdp,
            )
        }

    private fun request(
        url: String,
        method: String = "GET",
        contentType: String? = null,
        body: String? = null,
    ): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json, application/sdp")
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType)
            connection.doOutput = true
        }

        try {
            if (body != null) {
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }

            val status = connection.responseCode
            val responseStream = if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val response = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("error") }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: response.take(300).ifBlank { connection.responseMessage }
                throw IllegalStateException("Dashboard request failed ($status): $detail")
            }
            return response
        } finally {
            connection.disconnect()
        }
    }
}
