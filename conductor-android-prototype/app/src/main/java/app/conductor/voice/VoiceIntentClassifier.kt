package app.conductor.voice

class VoiceIntentClassifier(
    private val lifeDomainRouter: LifeDomainIntentRouter = LifeDomainIntentRouter()
) {
    fun classify(utterance: String): VoiceIntentClassification {
        val normalized = utterance.lowercase()
        if (controlKeywords.any { normalized.contains(it) }) {
            return VoiceIntentClassification(
                intentType = "os_control",
                sessionId = "voice_os_control",
                confidence = 0.88
            )
        }
        // Explicit task keywords keep the historical app_task route for taught task agents.
        if (taskKeywords.any { normalized.contains(it) }) {
            return VoiceIntentClassification(
                intentType = "app_task",
                sessionId = "voice_app_task",
                confidence = 0.9
            )
        }
        if (outdoorKeywords.any { normalized.contains(it) } &&
            !normalized.contains("calendar hold") &&
            !normalized.contains("add to calendar")
        ) {
            return VoiceIntentClassification(
                intentType = "outdoor_activity",
                sessionId = "voice_outdoor_activity",
                confidence = 0.92
            )
        }

        val life = lifeDomainRouter.route(utterance)
        if (life.domain != app.conductor.operator.accessibility.LifeDomain.OTHER) {
            return VoiceIntentClassification(
                intentType = life.intentType,
                sessionId = "voice_${life.intentType}",
                confidence = life.confidence
            )
        }

        return VoiceIntentClassification(
            intentType = "general_mobile_intent",
            sessionId = "voice_general_mobile_intent",
            confidence = 0.74
        )
    }

    private companion object {
        val controlKeywords = listOf(
            "stop autonomy", "ask only", "draft only", "trusted auto",
            "require approval", "always ask", "ask before", "cancel", "never mind", "nevermind"
        )
        val taskKeywords = listOf("task", "todo", "to-do", "remind", "reminder")
        val outdoorKeywords = listOf("outdoor", "outside", "event", "weather", "something to do outside")
    }
}

data class VoiceIntentClassification(
    val intentType: String,
    val sessionId: String,
    val confidence: Double
)
