package app.conductor.runtime

enum class AutonomyMode {
    ASK_ONLY,
    DRAFT_ONLY,
    LOW_RISK_AUTO,
    TRUSTED_AUTO
}

enum class Risk {
    LOW,
    MEDIUM,
    HIGH
}

enum class PolicyDecision {
    ALLOW,
    REQUIRE_APPROVAL,
    BLOCK
}

enum class StepStatus {
    SUCCEEDED,
    AWAITING_APPROVAL,
    BLOCKED,
    FAILED
}

enum class ApprovalStatus {
    PENDING,
    APPROVED,
    DENIED
}

data class Task(
    val id: String,
    val userId: String,
    val goal: String,
    val intentType: String = "outdoor_activity",
    val mode: AutonomyMode,
    val createdAtIso: String
)

data class ContextBundle(
    val id: String,
    val taskId: String,
    val purpose: String,
    val items: Map<String, ContextItem>
)

data class ContextItem(
    val source: String,
    val type: String,
    val summary: String,
    val sensitivity: String = "private",
    val accountId: String = "",
    val factId: String = "",
    val allowedPurpose: String = "",
    val expiresAtIso: String = "",
    val freshnessStatus: String = "",
    val baseGrantId: String = "",
    val appAgentId: String = "",
    val appAgentGrantId: String = ""
)

data class Plan(
    val id: String,
    val taskId: String,
    val goal: String,
    val recommendation: Recommendation,
    val steps: List<PlanStep>
)

data class Recommendation(
    val id: String,
    val title: String,
    val startsAtIso: String,
    val distanceMiles: Double,
    val score: Int
)

data class PlanStep(
    val id: String,
    val title: String,
    val tool: String,
    val actionType: String,
    val risk: Risk,
    val externalSideEffect: Boolean,
    val input: Map<String, String>
)

data class UserPolicy(
    val mode: AutonomyMode
)

data class PolicyResult(
    val decision: PolicyDecision,
    val reason: String
)

data class ApprovalCard(
    val id: String,
    val stepId: String,
    val actionType: String,
    val exactContent: String?,
    val recipient: String?,
    val reason: String,
    val status: ApprovalStatus = ApprovalStatus.PENDING
)

data class Verification(
    val status: String,
    val method: String
)

data class ToolResult(
    val stepId: String,
    val status: StepStatus,
    val policy: PolicyResult? = null,
    val approval: ApprovalCard? = null,
    val verification: Verification? = null,
    val error: String? = null
)

data class AuditEvent(
    val type: String,
    val detail: String
)

data class OperationTimeline(
    val id: String,
    val taskId: String,
    val userId: String,
    val intentType: String,
    val autonomyMode: AutonomyMode,
    val startedAtIso: String,
    val updatedAtIso: String,
    val events: List<OperationTimelineEvent>
)

data class OperationTimelineEvent(
    val id: String,
    val stepId: String,
    val actionType: String,
    val tool: String,
    val status: String,
    val policyDecision: String,
    val approvalId: String = "",
    val packageName: String = "",
    val playbookId: String = "",
    val sourceScope: List<String> = emptyList(),
    val summary: String
)
