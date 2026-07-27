package app.conductor.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class AndroidSpeechOutput(context: Context) : SpeechOutput {
    private var activeSessionId: String = "voice"
    private var ttsReady = false
    private val pendingDeltas = mutableListOf<String>()
    private lateinit var tts: TextToSpeech

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts.language = Locale.getDefault()
                flushPending()
            }
        }
    }

    override fun beginUtterance(sessionId: String) {
        activeSessionId = sessionId
        pendingDeltas.clear()
        tts.stop()
    }

    override fun speakDelta(text: String) {
        if (text.isBlank()) return
        if (!ttsReady) {
            pendingDeltas.add(text)
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "$activeSessionId:${System.nanoTime()}")
    }

    override fun finishUtterance() {
        flushPending()
    }

    override fun cancel(reason: String) {
        pendingDeltas.clear()
        tts.stop()
    }

    fun shutdown() {
        pendingDeltas.clear()
        tts.stop()
        tts.shutdown()
    }

    private fun flushPending() {
        val deltas = pendingDeltas.toList()
        pendingDeltas.clear()
        deltas.forEach(::speakDelta)
    }
}
