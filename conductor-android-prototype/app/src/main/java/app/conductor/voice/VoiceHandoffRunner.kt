package app.conductor.voice

class VoiceHandoffRunner(
    private val controller: VoiceSessionController,
    private val speechCapture: SpeechCapture,
    private val realtimeModelTransport: RealtimeModelTransport = RecordingRealtimeModelTransport(),
    private val speechOutput: SpeechOutput = RecordingSpeechOutput(),
    private val intentClassifier: VoiceIntentClassifier = VoiceIntentClassifier()
) {
    fun startMobileIntentCapture(
        autonomyMode: String,
        onHandoff: (VoiceIntentHandoff) -> Unit
    ): VoiceSnapshot {
        val snapshot = controller.startListening()
        speechCapture.start(object : SpeechCaptureCallback {
            override fun onPartialTranscript(text: String) {
                controller.receivePartial(text)
            }

            override fun onFinalTranscript(text: String) {
                controller.receiveFinal(text)
                val classification = intentClassifier.classify(text)
                realtimeModelTransport.streamResponse(
                    request = RealtimeModelRequest(
                        sessionId = classification.sessionId,
                        utterance = text,
                        intentHint = classification.intentType,
                        autonomyMode = autonomyMode
                    ),
                    callback = object : RealtimeModelCallback {
                        override fun onResponseStarted(text: String) {
                            controller.beginAssistantResponse(text)
                            speechOutput.beginUtterance(classification.sessionId)
                            speechOutput.speakDelta(text)
                        }

                        override fun onResponseDelta(text: String) {
                            controller.streamAssistantDelta(text)
                            speechOutput.speakDelta(text)
                        }

                        override fun onResponseFinished() {
                            controller.finishAssistantResponse()
                            speechOutput.finishUtterance()
                            onHandoff(
                                controller.handoffIntent(
                                    intentType = classification.intentType,
                                    confidence = classification.confidence
                                )
                            )
                        }

                        override fun onTransportError(reason: String) {
                            speechOutput.cancel(reason)
                            controller.interrupt(reason)
                        }
                    }
                )
            }

            override fun onCaptureError(reason: String) {
                speechOutput.cancel(reason)
                controller.interrupt(reason)
            }
        })
        return snapshot
    }

    fun startOutdoorIntentCapture(
        autonomyMode: String,
        onHandoff: (VoiceIntentHandoff) -> Unit
    ): VoiceSnapshot = startMobileIntentCapture(autonomyMode, onHandoff)

    fun cancel(reason: String = "voice_capture_cancelled"): VoiceSnapshot {
        speechCapture.cancel()
        realtimeModelTransport.cancel("voice_outdoor_activity")
        speechOutput.cancel(reason)
        return controller.interrupt(reason)
    }
}
