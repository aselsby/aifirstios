package app.conductor.voice

class RecordingSpeechOutput : SpeechOutput {
    private val spokenDeltas = mutableListOf<String>()
    private var activeSessionId: String? = null
    private var cancelledReason: String? = null

    override fun beginUtterance(sessionId: String) {
        activeSessionId = sessionId
        cancelledReason = null
        spokenDeltas.clear()
    }

    override fun speakDelta(text: String) {
        spokenDeltas.add(text)
    }

    override fun finishUtterance() {
        activeSessionId = null
    }

    override fun cancel(reason: String) {
        cancelledReason = reason
        activeSessionId = null
    }

    fun utteranceText(): String = spokenDeltas.joinToString("")
    fun currentSessionId(): String? = activeSessionId
    fun lastCancelledReason(): String? = cancelledReason
}
