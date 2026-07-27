package app.conductor.voice

interface SpeechOutput {
    fun beginUtterance(sessionId: String)
    fun speakDelta(text: String)
    fun finishUtterance()
    fun cancel(reason: String)
}
