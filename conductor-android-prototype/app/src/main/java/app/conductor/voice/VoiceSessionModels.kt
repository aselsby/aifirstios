package app.conductor.voice

enum class VoiceStatus {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    INTERRUPTED,
    HANDED_OFF
}

data class VoiceTurn(
    val role: String,
    val text: String
)

data class VoiceAuditEvent(
    val atIso: String,
    val type: String,
    val detail: String
)

data class VoiceSnapshot(
    val status: VoiceStatus,
    val partialTranscript: String,
    val finalTranscript: String,
    val assistantText: String,
    val turns: List<VoiceTurn>,
    val audit: List<VoiceAuditEvent>
)

data class VoiceIntentHandoff(
    val utterance: String,
    val intentType: String,
    val confidence: Double,
    val createdAtIso: String
)
