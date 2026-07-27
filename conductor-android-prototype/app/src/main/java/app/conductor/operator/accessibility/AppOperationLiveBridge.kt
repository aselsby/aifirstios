package app.conductor.operator.accessibility

import app.conductor.audit.AuditLedger
import app.conductor.runtime.Verification

interface AppOperationLiveBridge {
    fun dispatch(
        request: AppOperationRequest,
        playbook: AppOperationPlaybook
    ): AppOperationResult
}

/**
 * Production default for the tool/runtime path.
 *
 * Policy, grants, autonomy, and exact approvals are evaluated in [AppOperationExecutor],
 * then work is queued for [ConductorAccessibilityService] which owns the real UI tree bridge.
 * This class never marks an operation as verified.
 */
class AccessibilityQueueingLiveBridge(
    private val auditLedger: AuditLedger
) : AppOperationLiveBridge {
    override fun dispatch(
        request: AppOperationRequest,
        playbook: AppOperationPlaybook
    ): AppOperationResult {
        auditLedger.record(
            "operator.live_queue_required",
            "${request.id}:${request.packageName}:${playbook.id}"
        )
        return AppOperationResult(
            requestId = request.id,
            status = AppOperationStatus.NEEDS_HANDOFF,
            detail = "Live accessibility verification required for ${request.packageName}. Open the app so Conductor can operate it.",
            verification = Verification(
                status = "queued_for_accessibility",
                method = "accessibility_queue:${playbook.id}:awaiting_live_tree"
            )
        )
    }
}

/**
 * Deterministic test double. Must never be confused with live accessibility verification.
 * Verification method is explicitly labeled as recording_simulation.
 */
class RecordingAppOperationLiveBridge(
    private val auditLedger: AuditLedger
) : AppOperationLiveBridge {
    override fun dispatch(
        request: AppOperationRequest,
        playbook: AppOperationPlaybook
    ): AppOperationResult {
        for (step in playbook.steps) {
            auditLedger.record(
                "operator.step.ready",
                "${request.id}:${step.id} ${step.selectorHint} -> ${step.expectedState}"
            )
        }

        val verification = Verification(
            status = "simulated",
            method = "recording_simulation:${playbook.id}:post_state_receipt"
        )
        auditLedger.record("operator.verified", "${request.id} ${verification.method}")
        return AppOperationResult(
            requestId = request.id,
            status = AppOperationStatus.VERIFIED,
            detail = "SIMULATED ${playbook.id} with ${playbook.steps.size} steps (not live accessibility)",
            verification = verification
        )
    }
}
