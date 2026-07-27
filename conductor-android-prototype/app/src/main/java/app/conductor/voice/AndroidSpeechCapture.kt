package app.conductor.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class AndroidSpeechCapture(
    context: Context
) : SpeechCapture {
    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    override fun start(callback: SpeechCaptureCallback) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            callback.onCaptureError("speech_recognition_unavailable")
            return
        }

        val speechRecognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
        recognizer = speechRecognizer
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.bestResult()?.let(callback::onPartialTranscript)
            }

            override fun onResults(results: Bundle?) {
                val final = results.bestResult()
                if (final.isNullOrBlank()) {
                    callback.onCaptureError("speech_empty_result")
                } else {
                    callback.onFinalTranscript(final)
                }
            }

            override fun onError(error: Int) {
                callback.onCaptureError("speech_error_$error")
            }
        })
        speechRecognizer.startListening(recognizerIntent())
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell Conductor what to do")
        }

    private fun Bundle?.bestResult(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
