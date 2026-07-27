package app.conductor.operator.accessibility

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import app.conductor.audit.AuditLedger
import app.conductor.runtime.Verification

class AccessibilityAppOperationLiveBridge(
    private val auditLedger: AuditLedger,
    private val activeRootProvider: () -> AccessibilityNodeInfo?,
    private val activePackageProvider: () -> String? = { null },
    private val foregroundLauncher: AppForegroundLauncher = RecordingAppForegroundLauncher(auditLedger),
    private val cancellationRequested: () -> Boolean = { false }
) : AppOperationLiveBridge {
    override fun dispatch(
        request: AppOperationRequest,
        playbook: AppOperationPlaybook
    ): AppOperationResult {
        if (cancellationRequested()) {
            auditLedger.record("operator.live_cancelled", "${request.id}:before_start")
            return needsHandoff(request, "live_operation_cancelled")
        }

        val activePackageName = activePackageProvider()
        if (activePackageName != request.packageName) {
            val launch = foregroundLauncher.bringToForeground(request.packageName)
            return when (launch.status) {
                AppForegroundLaunchStatus.ALREADY_FOREGROUND -> {
                    auditLedger.record("operator.live_foreground_verified", "${request.id}:${request.packageName}")
                    dispatch(request, playbook)
                }
                AppForegroundLaunchStatus.LAUNCHED -> {
                    auditLedger.record("operator.live_foreground_launch_pending", "${request.id}:${launch.detail}")
                    needsHandoff(request, "foreground_launch_pending:${request.packageName}")
                }
                AppForegroundLaunchStatus.FAILED -> {
                    auditLedger.record("operator.live_foreground_launch_failed", "${request.id}:${launch.detail}")
                    needsHandoff(request, launch.detail)
                }
            }
        }

        var root = activeRootProvider()
            ?: return needsHandoff(request, "active_window_missing")

        if (!hasUniqueVisibleLabel(root, playbook.accountProofLabel)) {
            auditLedger.record("operator.live_account_proof_handoff", "${request.id}:${playbook.accountProofLabel}")
            return needsHandoff(request, "account_proof_missing_or_ambiguous")
        }

        for (step in playbook.steps) {
            if (cancellationRequested()) {
                auditLedger.record("operator.live_cancelled", "${request.id}:${step.id}:before_action")
                return needsHandoff(request, "live_operation_cancelled")
            }

            val selectorLabel = materialize(step.selectorHint, request.input)
            val target = uniqueVisibleNode(root, selectorLabel) ?: recoverAndFindTarget(
                request = request,
                step = step,
                root = root,
                selectorLabel = selectorLabel,
                input = request.input
            )?.also {
                root = activeRootProvider()
                    ?: return needsHandoff(request, "active_window_missing_after_recovery")
            }
            if (target == null) {
                auditLedger.record("operator.live_selector_handoff", "${request.id}:${step.id}:$selectorLabel")
                return needsHandoff(request, "selector_missing_or_ambiguous:$selectorLabel")
            }

            val actionResult = performStepAction(target, step, request.input)
            if (!actionResult) {
                auditLedger.record("operator.live_action_handoff", "${request.id}:${step.id}:$selectorLabel")
                return needsHandoff(request, "action_failed:$selectorLabel")
            }

            if (cancellationRequested()) {
                auditLedger.record("operator.live_cancelled", "${request.id}:${step.id}:after_action")
                return needsHandoff(request, "live_operation_cancelled")
            }

            root = activeRootProvider()
                ?: return needsHandoff(request, "active_window_missing_after_action")
            val expectedLabel = materialize(step.expectedState, request.input)
            if (!hasUniqueVisibleLabel(root, expectedLabel)) {
                auditLedger.record("operator.live_verifier_handoff", "${request.id}:${step.id}:$expectedLabel")
                return needsHandoff(request, "verifier_missing_or_ambiguous:$expectedLabel")
            }
            auditLedger.record("operator.live_step_executed", "${request.id}:${step.id}:$selectorLabel")
        }

        val verification = Verification(
            status = "verified",
            method = "accessibility_live_tree:${playbook.id}:post_state_receipt"
        )
        auditLedger.record("operator.live_verified", "${request.id}:${verification.method}")
        return AppOperationResult(
            requestId = request.id,
            status = AppOperationStatus.VERIFIED,
            detail = "Verified ${playbook.id} against the active accessibility tree",
            verification = verification
        )
    }

    private fun needsHandoff(request: AppOperationRequest, reason: String): AppOperationResult =
        AppOperationResult(
            requestId = request.id,
            status = AppOperationStatus.NEEDS_HANDOFF,
            detail = reason
        )

    private fun hasUniqueVisibleLabel(root: AccessibilityNodeInfo, label: String): Boolean {
        if (label.isBlank()) return false
        return uniqueVisibleNode(root, label) != null
    }

    private fun uniqueVisibleNode(root: AccessibilityNodeInfo, label: String): AccessibilityNodeInfo? {
        if (label.isBlank()) return null
        val matches = root.findAccessibilityNodeInfosByText(label).filter {
            it.isVisibleToUser && it.isEnabled
        }
        return matches.singleOrNull()
    }

    private fun performStepAction(
        target: AccessibilityNodeInfo,
        step: AppOperationStep,
        input: Map<String, String>
    ): Boolean {
        val value = inputValueFor(step, input)
        return when (step.operation) {
            "set_text" -> {
                if (value.isNullOrBlank()) return false
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        value
                    )
                }
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            "click" -> target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            else -> if (value != null && shouldSetText(step)) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        value
                    )
                }
                target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            } else {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        }
    }

    private fun recoverAndFindTarget(
        request: AppOperationRequest,
        step: AppOperationStep,
        root: AccessibilityNodeInfo,
        selectorLabel: String,
        input: Map<String, String>
    ): AccessibilityNodeInfo? {
        val recoveryLabel = step.recoverySelectorHints
            .map { materialize(it, input) }
            .firstOrNull { uniqueVisibleNode(root, it) != null }
            ?: return null
        val recoveryNode = uniqueVisibleNode(root, recoveryLabel) ?: return null
        auditLedger.record("operator.live_recovery_attempted", "${request.id}:${step.id}:$recoveryLabel")
        if (!recoveryNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            auditLedger.record("operator.live_recovery_failed", "${request.id}:${step.id}:$recoveryLabel")
            return null
        }
        val recoveredRoot = activeRootProvider()
            ?: return null.also {
                auditLedger.record("operator.live_recovery_failed", "${request.id}:${step.id}:active_window_missing_after_recovery")
            }
        val recoveredTarget = uniqueVisibleNode(recoveredRoot, selectorLabel)
        if (recoveredTarget == null) {
            auditLedger.record("operator.live_recovery_handoff", "${request.id}:${step.id}:$selectorLabel")
        } else {
            auditLedger.record("operator.live_recovery_succeeded", "${request.id}:${step.id}:$selectorLabel")
        }
        return recoveredTarget
    }

    private fun shouldSetText(step: AppOperationStep): Boolean {
        val label = step.selectorHint.lowercase()
        return label.contains("field") ||
            label.contains("box") ||
            label.contains("composer") ||
            label.contains("body") ||
            label.contains("title and time")
    }

    private fun materialize(template: String, input: Map<String, String>): String =
        inputKeyFrom(template)?.let { key ->
            template.replace("input.$key", input[key].orEmpty())
        } ?: template

    private fun inputValueFor(step: AppOperationStep, input: Map<String, String>): String? =
        step.inputKey.takeIf { it.isNotBlank() }?.let { input[it] }
            ?: inputKeyFrom(step.expectedState)?.let { input[it] }
            ?: inputKeyFrom(step.selectorHint)?.let { input[it] }

    private fun inputKeyFrom(text: String): String? {
        val marker = "input."
        val start = text.indexOf(marker)
        if (start < 0) return null
        return text
            .drop(start + marker.length)
            .takeWhile { it.isLetterOrDigit() || it == '_' }
            .takeIf { it.isNotBlank() }
    }
}
