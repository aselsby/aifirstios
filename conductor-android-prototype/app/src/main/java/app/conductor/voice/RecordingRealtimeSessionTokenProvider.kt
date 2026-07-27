package app.conductor.voice

class RecordingRealtimeSessionTokenProvider : RealtimeSessionTokenProvider {
    private val issuedTokens = mutableListOf<RealtimeSessionToken>()

    override fun createSessionToken(intentHint: String, autonomyMode: String): RealtimeSessionToken {
        val token = RealtimeSessionToken(
            value = "ephemeral:test:$intentHint:$autonomyMode",
            expiresAtIso = "2026-07-27T10:50:00-05:00",
            model = "realtime-mobile-os-preview",
            scope = "voice:intent_handoff"
        )
        issuedTokens.add(token)
        return token
    }

    fun allIssued(): List<RealtimeSessionToken> =
        issuedTokens.toList()
}
