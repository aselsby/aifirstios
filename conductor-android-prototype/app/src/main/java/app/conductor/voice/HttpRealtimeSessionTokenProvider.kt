package app.conductor.voice

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpRealtimeSessionTokenProvider(
    private val baseUrl: String = "http://10.0.2.2:8787",
    private val authTokenProvider: MobileAuthTokenProvider = RecordingMobileAuthTokenProvider()
) : RealtimeSessionTokenProvider {
    override fun createSessionToken(intentHint: String, autonomyMode: String): RealtimeSessionToken? {
        val bearerToken = authTokenProvider.bearerToken() ?: return null
        val connection = (URL("$baseUrl/realtime/session-token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 2500
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearerToken")
        }

        return try {
            val requestBody = JSONObject()
                .put("intentHint", intentHint)
                .put("autonomyMode", autonomyMode)
                .put("scope", "voice:intent_handoff")
                .put("ttlSeconds", 120)
                .toString()

            connection.outputStream.use { stream ->
                stream.write(requestBody.toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return null
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val response = JSONObject(responseBody)
            if (response.optString("status") != "issued") {
                return null
            }

            val token = response.getJSONObject("token")
            RealtimeSessionToken(
                value = token.getString("value"),
                expiresAtIso = token.getString("expiresAtIso"),
                model = token.getString("model"),
                scope = token.getString("scope")
            )
        } catch (error: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
