package app.conductor.operator.accessibility

import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.Verification

enum class AppOperationStatus {
    READY,
    NEEDS_HANDOFF,
    BLOCKED,
    VERIFIED
}

enum class AppAgentRouteStatus {
    READY,
    NEEDS_LOGIN,
    NEEDS_GRANT,
    BLOCKED
}

data class AppOperationRequest(
    val id: String,
    val userId: String = "user_001",
    val packageName: String,
    val playbookId: String,
    val approvalReceipt: AppOperationApprovalReceipt? = null,
    val requiredSourceIds: Set<String> = emptySet(),
    val input: Map<String, String>
)

data class AppOperationApprovalReceipt(
    val approvalId: String,
    val actionType: String,
    val approvedExactContent: String
)

data class AppOperationStep(
    val id: String,
    val description: String,
    val selectorHint: String,
    val expectedState: String,
    val operation: String = "auto",
    val inputKey: String = "",
    val recoverySelectorHints: List<String> = emptyList()
)

data class AppOperationPlaybook(
    val id: String,
    val packageName: String,
    val actionType: String,
    val riskLabel: String,
    val requiresExactApproval: Boolean,
    val invocationPhrases: Set<String> = emptySet(),
    val accountProofLabel: String = "",
    val requiredInputKeys: Set<String> = emptySet(),
    val requiredSourceIds: Set<String> = emptySet(),
    val steps: List<AppOperationStep>
)

data class AppOperationResult(
    val requestId: String,
    val status: AppOperationStatus,
    val detail: String,
    val verification: Verification? = null
)

enum class AppLoginState {
    LOGGED_OUT,
    LOGGED_IN
}

data class AppLoginProof(
    val method: String,
    val subjectLabel: String,
    val verifiedAtIso: String
)

data class AppOperationSession(
    val userId: String,
    val packageName: String,
    val loginState: AppLoginState,
    val autonomyMode: AutonomyMode,
    val allowedPlaybookIds: Set<String>,
    val allowedSourceIds: Set<String> = emptySet(),
    val approvalRequiredActionTypes: Set<String> = emptySet(),
    val remainingAutonomousActions: Int = 3,
    val loginProof: AppLoginProof = AppLoginProof(
        method = "seeded_scaffold_login",
        subjectLabel = packageName,
        verifiedAtIso = "2026-07-27T10:45:00-05:00"
    ),
    val revoked: Boolean = false,
    val expiresAtIso: String
) {
    fun allows(playbookId: String): Boolean =
        !revoked &&
            loginState == AppLoginState.LOGGED_IN &&
            hasLoginProof() &&
            (allowedPlaybookIds.contains("*") || allowedPlaybookIds.contains(playbookId))

    fun requiresApprovalFor(actionType: String): Boolean =
        approvalRequiredActionTypes.contains(actionType)

    fun hasLoginProof(): Boolean =
        loginProof.method.isNotBlank() &&
            loginProof.subjectLabel.isNotBlank() &&
            loginProof.verifiedAtIso.isNotBlank()

    fun isExpired(nowIso: String): Boolean = expiresAtIso <= nowIso
}

data class AppAgentSurface(
    val id: String,
    val appName: String,
    val packageName: String,
    val loginState: AppLoginState,
    val autonomyMode: AutonomyMode,
    val allowedPlaybookIds: Set<String>,
    val supportedActionTypes: Set<String>,
    val allowedSourceIds: Set<String>
)

data class AppAgentDiscovery(
    val packageName: String,
    val observedAtIso: String,
    val visibleLabelCounts: Map<String, Int>,
    val accountProofCandidates: Set<String>,
    val bounded: Boolean
)

data class AppAgentRoute(
    val status: AppAgentRouteStatus,
    val actionType: String,
    val surface: AppAgentSurface? = null,
    val playbook: AppOperationPlaybook? = null,
    val reason: String? = null,
    val missingSourceIds: Set<String> = emptySet()
)

data class AppOperationQueueItem(
    val request: AppOperationRequest,
    val reason: String,
    val requiredUserAction: String,
    val primaryActionLabel: String = "Grant and retry",
    val autonomyContext: String = "",
    val createdAtIso: String = "2026-07-27T10:45:00-05:00",
    val expiresAtIso: String = "2026-07-27T11:15:00-05:00"
) {
    fun isExpired(nowIso: String): Boolean = expiresAtIso <= nowIso
}
