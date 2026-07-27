package app.conductor.voice

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder

class VoiceSessionService : Service() {
    private val binder = VoiceSessionBinder()
    private val controller = VoiceSessionController()
    private val speechCapture by lazy { AndroidSpeechCapture(applicationContext) }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startPushToTalkSession(): VoiceSnapshot {
        val snapshot = controller.startListening()
        speechCapture.start(object : SpeechCaptureCallback {
            override fun onPartialTranscript(text: String) {
                controller.receivePartial(text)
            }

            override fun onFinalTranscript(text: String) {
                controller.receiveFinal(text)
            }

            override fun onCaptureError(reason: String) {
                controller.interrupt(reason)
            }
        })
        return snapshot
    }

    fun receivePartialTranscript(text: String): VoiceSnapshot =
        controller.receivePartial(text)

    fun receiveFinalTranscript(text: String): VoiceSnapshot =
        controller.receiveFinal(text)

    fun interrupt(reason: String): VoiceSnapshot {
        speechCapture.cancel()
        return controller.interrupt(reason)
    }

    fun handoffIntent(): VoiceIntentHandoff =
        controller.handoffIntent()

    fun snapshot(): VoiceSnapshot =
        controller.snapshot()

    inner class VoiceSessionBinder : Binder() {
        fun service(): VoiceSessionService = this@VoiceSessionService
    }
}
