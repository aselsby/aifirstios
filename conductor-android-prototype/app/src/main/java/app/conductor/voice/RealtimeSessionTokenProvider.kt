package app.conductor.voice

data class RealtimeSessionToken(
    val value: String,
    val expiresAtIso: String,
    val model: String,
    val scope: String
)

interface RealtimeSessionTokenProvider {
    fun createSessionToken(intentHint: String, autonomyMode: String): RealtimeSessionToken?
}
