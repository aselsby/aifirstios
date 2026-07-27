package app.conductor.voice

/**
 * Production-shaped realtime transport boundary.
 *
 * Token issuance is real (ephemeral server session). Model streaming is still a
 * scaffold until the websocket provider is wired; responses are intent-aware
 * and never claim external app work completed.
 */
class ProductionRealtimeModelTransport(
    private val sessionTokenProvider: RealtimeSessionTokenProvider
) : RealtimeModelTransport {
    override fun streamResponse(request: RealtimeModelRequest, callback: RealtimeModelCallback) {
        val token = sessionTokenProvider.createSessionToken(
            intentHint = request.intentHint,
            autonomyMode = request.autonomyMode
        )

        if (token == null) {
            callback.onTransportError("realtime_session_token_unavailable")
            return
        }

        val utterance = request.utterance
        val mode = request.autonomyMode
        callback.onResponseStarted("Got it. ")
        callback.onResponseDelta(
            when {
                utterance.contains("outside", ignoreCase = true) ||
                    utterance.contains("outdoor", ignoreCase = true) ->
                    "I'll check calendar, weather, and nearby events under $mode autonomy, then draft next steps without posting or messaging until approved."
                utterance.contains("post", ignoreCase = true) ||
                    utterance.contains("facebook", ignoreCase = true) ->
                    "Public posts require exact approval. I'll prepare the plan and pause before anything leaves the device."
                utterance.contains("message", ignoreCase = true) ||
                    utterance.contains("invite", ignoreCase = true) ||
                    utterance.contains("text", ignoreCase = true) ->
                    "I'll prepare the message under $mode autonomy and ask before sending."
                else ->
                    "I'll hand this off as a structured mobile intent under $mode autonomy and only act through approved tools and app skills."
            }
        )
        callback.onResponseFinished()
    }

    override fun cancel(sessionId: String) {
        // Production implementations should close the active websocket or streaming session here.
    }
}
