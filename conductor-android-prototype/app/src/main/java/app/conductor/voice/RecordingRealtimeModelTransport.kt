package app.conductor.voice

class RecordingRealtimeModelTransport(
    private val opening: String = "I found a good outdoor option. ",
    private val delta: String = "I will prepare the plan and pause before sending anything."
) : RealtimeModelTransport {
    private val requests = mutableListOf<RealtimeModelRequest>()

    override fun streamResponse(request: RealtimeModelRequest, callback: RealtimeModelCallback) {
        requests.add(request)
        callback.onResponseStarted(opening)
        callback.onResponseDelta(delta)
        callback.onResponseFinished()
    }

    override fun cancel(sessionId: String) {
        requests.removeAll { it.sessionId == sessionId }
    }

    fun allRequests(): List<RealtimeModelRequest> =
        requests.toList()
}
