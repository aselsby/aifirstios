package app.conductor.tools.intents

data class AndroidIntentPlan(
    val id: String,
    val action: String,
    val dataUri: String? = null,
    val packageName: String? = null,
    val extras: Map<String, String> = emptyMap(),
    val verificationMethod: String,
    val requiresUserFinalTap: Boolean
)

enum class AndroidIntentLaunchStatus {
    LAUNCHED,
    NEEDS_HANDOFF,
    FAILED
}

data class AndroidIntentLaunchResult(
    val status: AndroidIntentLaunchStatus,
    val planId: String,
    val detail: String,
    val verificationMethod: String
)

enum class AndroidIntentStatus {
    READY,
    NEEDS_HANDOFF,
    UNSUPPORTED
}

data class AndroidIntentResult(
    val status: AndroidIntentStatus,
    val plan: AndroidIntentPlan? = null,
    val reason: String? = null
)
