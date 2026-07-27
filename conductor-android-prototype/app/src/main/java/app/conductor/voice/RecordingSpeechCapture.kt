package app.conductor.voice

class RecordingSpeechCapture(
    private val partial: String = "Find me something outdoors",
    private val final: String = "Find me something outdoors to do this afternoon and invite Maya if it fits."
) : SpeechCapture {
    var started = false
        private set

    override fun start(callback: SpeechCaptureCallback) {
        started = true
        callback.onPartialTranscript(partial)
        callback.onFinalTranscript(final)
    }

    override fun stop() {
        started = false
    }

    override fun cancel() {
        started = false
    }
}
