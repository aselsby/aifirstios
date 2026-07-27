package app.conductor.voice

class FallbackRealtimeSessionTokenProvider(
    private val primary: RealtimeSessionTokenProvider,
    private val fallback: RealtimeSessionTokenProvider
) : RealtimeSessionTokenProvider {
    override fun createSessionToken(intentHint: String, autonomyMode: String): RealtimeSessionToken? =
        primary.createSessionToken(intentHint, autonomyMode)
            ?: fallback.createSessionToken(intentHint, autonomyMode)
}
