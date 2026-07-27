package app.conductor.voice

import app.conductor.runtime.SystemClock

class VoiceSessionController(
    private val nowIso: () -> String = { SystemClock.nowIso() }
) {
    private var status = VoiceStatus.IDLE
    private var partialTranscript = ""
    private var finalTranscript = ""
    private var assistantText = ""
    private val turns = mutableListOf<VoiceTurn>()
    private val audit = mutableListOf<VoiceAuditEvent>()

    fun startListening(): VoiceSnapshot {
        status = VoiceStatus.LISTENING
        partialTranscript = ""
        record("voice.listening_started", "Push-to-talk session started.")
        return snapshot()
    }

    fun receivePartial(text: String): VoiceSnapshot {
        requireStatus(VoiceStatus.LISTENING, "Partial transcript requires listening state.")
        partialTranscript = text
        record("voice.partial", text)
        return snapshot()
    }

    fun receiveFinal(text: String): VoiceSnapshot {
        requireStatus(VoiceStatus.LISTENING, "Final transcript requires listening state.")
        finalTranscript = text.trim()
        partialTranscript = ""
        status = VoiceStatus.THINKING
        turns.add(VoiceTurn(role = "user", text = finalTranscript))
        record("voice.final", finalTranscript)
        return snapshot()
    }

    fun beginAssistantResponse(text: String = ""): VoiceSnapshot {
        requireStatus(VoiceStatus.THINKING, "Assistant response requires thinking state.")
        status = VoiceStatus.SPEAKING
        assistantText = text
        record("assistant.started", text)
        return snapshot()
    }

    fun streamAssistantDelta(text: String): VoiceSnapshot {
        requireStatus(VoiceStatus.SPEAKING, "Assistant deltas require speaking state.")
        assistantText += text
        record("assistant.delta", text)
        return snapshot()
    }

    fun finishAssistantResponse(): VoiceSnapshot {
        requireStatus(VoiceStatus.SPEAKING, "Finishing response requires speaking state.")
        turns.add(VoiceTurn(role = "assistant", text = assistantText))
        status = VoiceStatus.IDLE
        record("assistant.finished", assistantText)
        return snapshot()
    }

    fun interrupt(reason: String = "user_interrupted"): VoiceSnapshot {
        if (status != VoiceStatus.SPEAKING && status != VoiceStatus.THINKING && status != VoiceStatus.LISTENING) {
            return snapshot()
        }
        status = VoiceStatus.INTERRUPTED
        record("voice.interrupted", reason)
        return snapshot()
    }

    fun resumeListening(): VoiceSnapshot {
        status = VoiceStatus.LISTENING
        partialTranscript = ""
        record("voice.listening_resumed", "User resumed voice input.")
        return snapshot()
    }

    fun handoffIntent(
        intentType: String = "outdoor_activity",
        confidence: Double = 0.9
    ): VoiceIntentHandoff {
        if (finalTranscript.isBlank()) {
            error("Cannot hand off without a final transcript.")
        }
        status = VoiceStatus.HANDED_OFF
        val handoff = VoiceIntentHandoff(
            utterance = finalTranscript,
            intentType = intentType,
            confidence = confidence,
            createdAtIso = nowIso()
        )
        record("intent.handed_off", "$intentType:$finalTranscript")
        return handoff
    }

    fun snapshot(): VoiceSnapshot =
        VoiceSnapshot(
            status = status,
            partialTranscript = partialTranscript,
            finalTranscript = finalTranscript,
            assistantText = assistantText,
            turns = turns.toList(),
            audit = audit.toList()
        )

    private fun record(type: String, detail: String) {
        audit.add(
            0,
            VoiceAuditEvent(
                atIso = nowIso(),
                type = type,
                detail = detail
            )
        )
    }

    private fun requireStatus(expected: VoiceStatus, message: String) {
        if (status != expected) {
            error("$message Current status: $status")
        }
    }
}
