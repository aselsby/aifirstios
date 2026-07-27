package app.conductor.voice

interface SpeechCapture {
    fun start(callback: SpeechCaptureCallback)
    fun stop()
    fun cancel()
}

interface SpeechCaptureCallback {
    fun onPartialTranscript(text: String)
    fun onFinalTranscript(text: String)
    fun onCaptureError(reason: String)
}
