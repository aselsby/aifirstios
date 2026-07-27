package app.conductor.voice

class VoiceIntentClassifier {
    fun classify(utterance: String): VoiceIntentClassification {
        val normalized = utterance.lowercase()
        return when {
            controlKeywords.any { normalized.contains(it) } -> VoiceIntentClassification(
                intentType = "os_control",
                sessionId = "voice_os_control",
                confidence = 0.88
            )
            taskKeywords.any { normalized.contains(it) } -> VoiceIntentClassification(
                intentType = "app_task",
                sessionId = "voice_app_task",
                confidence = 0.9
            )
            outdoorKeywords.any { normalized.contains(it) } -> VoiceIntentClassification(
                intentType = "outdoor_activity",
                sessionId = "voice_outdoor_activity",
                confidence = 0.92
            )
            else -> VoiceIntentClassification(
                intentType = "general_mobile_intent",
                sessionId = "voice_general_mobile_intent",
                confidence = 0.74
            )
        }
    }

    private companion object {
        val controlKeywords = listOf("stop autonomy", "ask only", "draft only", "trusted auto", "require approval", "always ask", "ask before", "cancel", "never mind", "nevermind")
        val taskKeywords = listOf("task", "todo", "to-do", "remind", "reminder")
        val outdoorKeywords = listOf("outdoor", "outside", "event", "weather", "calendar")
    }
}

data class VoiceIntentClassification(
    val intentType: String,
    val sessionId: String,
    val confidence: Double
)
