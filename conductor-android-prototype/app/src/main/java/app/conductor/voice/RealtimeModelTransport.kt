package app.conductor.voice

data class RealtimeModelRequest(
    val sessionId: String,
    val utterance: String,
    val intentHint: String,
    val autonomyMode: String
)

interface RealtimeModelTransport {
    fun streamResponse(request: RealtimeModelRequest, callback: RealtimeModelCallback)
    fun cancel(sessionId: String)
}

interface RealtimeModelCallback {
    fun onResponseStarted(text: String)
    fun onResponseDelta(text: String)
    fun onResponseFinished()
    fun onTransportError(reason: String)
}
