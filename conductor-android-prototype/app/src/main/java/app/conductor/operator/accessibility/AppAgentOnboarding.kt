package app.conductor.operator.accessibility

import app.conductor.audit.AuditLedger
import app.conductor.graph.AppAgentGrant
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore

class AppAgentOnboarding(
    private val auditLedger: AuditLedger,
    private val recordStore: ConductorRecordStore,
    private val appAgentId: String = "conductor.voice",
    private val purpose: String = "activity_planning"
) {
    fun onboardFromDiscovery(
        userId: String,
        appName: String,
        discovery: AppAgentDiscovery,
        playbook: AppOperationPlaybook,
        allowedSourceIds: Set<String> = emptySet(),
        autonomyMode: AutonomyMode = AutonomyMode.DRAFT_ONLY
    ): AppAgentOnboardingResult {
        if (playbook.packageName != discovery.packageName) {
            auditLedger.record(
                "app_agent.discovery_onboarding_blocked",
                "${playbook.id}:package_mismatch:${playbook.packageName}:${discovery.packageName}"
            )
            return AppAgentOnboardingResult(
                status = AppOperationStatus.BLOCKED,
                detail = "Observed app does not match the playbook package."
            )
        }
        if (discovery.visibleLabelCounts.isEmpty()) {
            auditLedger.record("app_agent.discovery_onboarding_blocked", "${playbook.id}:empty_discovery")
            return AppAgentOnboardingResult(
                status = AppOperationStatus.BLOCKED,
                detail = "No observed labels are available for this app."
            )
        }
        if (!discovery.accountProofCandidates.contains(playbook.accountProofLabel)) {
            auditLedger.record(
                "app_agent.discovery_onboarding_blocked",
                "${playbook.id}:account_proof_not_observed:${playbook.accountProofLabel}"
            )
            return AppAgentOnboardingResult(
                status = AppOperationStatus.BLOCKED,
                detail = "The observed app snapshot does not prove the requested signed-in account label."
            )
        }
        auditLedger.record(
            "app_agent.discovery_onboarding_started",
            "${discovery.packageName}:${discovery.visibleLabelCounts.size}:${playbook.id}"
        )
        return onboard(
            userId = userId,
            appName = appName,
            playbook = playbook,
            allowedSourceIds = allowedSourceIds,
            autonomyMode = autonomyMode,
            observedTreeLabelCounts = discovery.visibleLabelCounts
        )
    }

    fun onboard(
        userId: String,
        appName: String,
        playbook: AppOperationPlaybook,
        allowedSourceIds: Set<String> = emptySet(),
        autonomyMode: AutonomyMode = AutonomyMode.DRAFT_ONLY,
        observedTreeLabels: Set<String> = emptySet(),
        observedTreeLabelCounts: Map<String, Int> = observedTreeLabels.associateWith { 1 }
    ): AppAgentOnboardingResult {
        val errors = validate(playbook)
        if (errors.isNotEmpty()) {
            auditLedger.record("app_agent.onboarding_blocked", "${playbook.id}:${errors.joinToString()}")
            return AppAgentOnboardingResult(
                status = AppOperationStatus.BLOCKED,
                detail = "Invalid app-agent playbook: ${errors.joinToString()}"
            )
        }
        val scopedPlaybook = playbook.copy(
            requiredSourceIds = playbook.requiredSourceIds.ifEmpty { allowedSourceIds }
        )
        val dryRun = dryRun(scopedPlaybook, observedTreeLabelCounts)
        if (!dryRun.verified) {
            auditLedger.record("app_agent.onboarding_blocked", "${scopedPlaybook.id}:${dryRun.reason}")
            return AppAgentOnboardingResult(
                status = AppOperationStatus.BLOCKED,
                detail = "App-agent playbook dry-run failed: ${dryRun.reason}",
                dryRun = dryRun
            )
        }

        val expiresAtIso = "2026-07-27T18:00:00-05:00"
        recordStore.saveAppOperationPlaybook(scopedPlaybook)
        recordStore.saveAppOperationSession(
            AppOperationSession(
                userId = userId,
                packageName = scopedPlaybook.packageName,
                loginState = AppLoginState.LOGGED_IN,
                autonomyMode = autonomyMode,
                allowedPlaybookIds = setOf(scopedPlaybook.id),
                allowedSourceIds = allowedSourceIds,
                approvalRequiredActionTypes = if (scopedPlaybook.requiresExactApproval) {
                    setOf(scopedPlaybook.actionType)
                } else {
                    emptySet()
                },
                loginProof = AppLoginProof(
                    method = "observed_accessibility_tree_dry_run",
                    subjectLabel = scopedPlaybook.accountProofLabel,
                    verifiedAtIso = SystemClock.nowIso()
                ),
                expiresAtIso = expiresAtIso
            )
        )
        if (allowedSourceIds.isNotEmpty()) {
            val existingGrant = recordStore.appAgentGrants().firstOrNull {
                it.id == grantIdFor(scopedPlaybook)
            }
            if (existingGrant?.revoked == true) {
                auditLedger.record(
                    "app_agent.onboarding_source_grant_preserved_revoked",
                    "${scopedPlaybook.packageName}:${allowedSourceIds.joinToString()}"
                )
            } else {
                recordStore.saveAppAgentGrant(
                    AppAgentGrant(
                        id = grantIdFor(scopedPlaybook),
                        appAgentId = appAgentId,
                        packageName = scopedPlaybook.packageName,
                        purposes = existingGrant?.purposes.orEmpty() + purpose,
                        sources = existingGrant?.sources.orEmpty() + allowedSourceIds,
                        revoked = false,
                        expiresAtIso = expiresAtIso
                    )
                )
                auditLedger.record(
                    "app_agent.onboarding_source_grant_created",
                    "${scopedPlaybook.packageName}:${allowedSourceIds.joinToString()}"
                )
            }
        }
        auditLedger.record("app_agent.onboarded", "$appName:${scopedPlaybook.packageName}:${scopedPlaybook.id}")
        return AppAgentOnboardingResult(
            status = AppOperationStatus.VERIFIED,
            detail = "Onboarded $appName as an app-agent surface for ${scopedPlaybook.actionType}",
            dryRun = dryRun
        )
    }

    private fun grantIdFor(playbook: AppOperationPlaybook): String =
        "agent_grant_${playbook.packageName}_${playbook.id}"
            .replace(".", "_")

    private fun validate(playbook: AppOperationPlaybook): List<String> {
        val errors = mutableListOf<String>()
        if (playbook.id.isBlank()) errors += "missing_id"
        if (playbook.packageName.isBlank()) errors += "missing_package"
        if (playbook.actionType.isBlank()) errors += "missing_action_type"
        if (playbook.invocationPhrases.any { it.isBlank() }) errors += "blank_invocation_phrase"
        if (playbook.accountProofLabel.isBlank()) errors += "missing_account_proof_label"
        if (playbook.requiredInputKeys.isEmpty()) errors += "missing_required_inputs"
        if (playbook.steps.isEmpty()) errors += "missing_steps"
        playbook.steps.forEach { step ->
            if (!setOf("auto", "click", "set_text").contains(step.operation)) {
                errors += "unsupported_step_operation:${step.id}:${step.operation}"
            }
            if (step.operation == "set_text" && step.inputKey.isBlank()) {
                errors += "set_text_requires_input_key:${step.id}"
            }
            if (step.inputKey.isNotBlank() && !playbook.requiredInputKeys.contains(step.inputKey)) {
                errors += "step_input_not_declared:${step.id}:${step.inputKey}"
            }
            if (step.recoverySelectorHints.size > 3) {
                errors += "too_many_recovery_hints:${step.id}"
            }
            if (step.recoverySelectorHints.any { it.isBlank() }) {
                errors += "blank_recovery_hint:${step.id}"
            }
        }
        if (playbook.requiresExactApproval && !playbook.requiredInputKeys.contains("exactBody")) {
            errors += "sensitive_playbook_requires_exactBody"
        }
        if (playbook.requiresExactApprovalRisk() && !playbook.requiresExactApproval) {
            errors += "public_or_high_risk_requires_exact_approval"
        }
        if (playbook.requiresExactApprovalRisk() && !playbook.requiredInputKeys.contains("exactBody")) {
            errors += "public_or_high_risk_requires_exactBody"
        }
        return errors
    }

    private fun dryRun(
        playbook: AppOperationPlaybook,
        observedTreeLabelCounts: Map<String, Int>
    ): AppAgentPlaybookDryRun {
        if (observedTreeLabelCounts.isEmpty()) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "observed_tree_required"
            )
        }

        val missingStep = playbook.steps.firstOrNull { step ->
            observedTreeLabelCounts[step.selectorHint] == null
        }
        if (missingStep != null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "target_missing:${missingStep.selectorHint}"
            )
        }

        val ambiguousStep = playbook.steps.firstOrNull { step ->
            (observedTreeLabelCounts[step.selectorHint] ?: 0) > 1
        }
        if (ambiguousStep != null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "target_ambiguous:${ambiguousStep.selectorHint}"
            )
        }

        val missingExpectedState = playbook.steps.firstOrNull { step ->
            observedTreeLabelCounts[step.expectedState] == null
        }
        if (missingExpectedState != null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "verifier_missing:${missingExpectedState.expectedState}"
            )
        }

        val ambiguousExpectedState = playbook.steps.firstOrNull { step ->
            (observedTreeLabelCounts[step.expectedState] ?: 0) > 1
        }
        if (ambiguousExpectedState != null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "verifier_ambiguous:${ambiguousExpectedState.expectedState}"
            )
        }

        val missingRecovery = playbook.steps
            .flatMap { step -> step.recoverySelectorHints.map { step.id to it } }
            .firstOrNull { (_, hint) -> observedTreeLabelCounts[hint] == null }
        if (missingRecovery != null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "recovery_missing:${missingRecovery.first}:${missingRecovery.second}"
            )
        }

        val ambiguousRecovery = playbook.steps
            .flatMap { step -> step.recoverySelectorHints.map { step.id to it } }
            .firstOrNull { (_, hint) -> (observedTreeLabelCounts[hint] ?: 0) > 1 }
        if (ambiguousRecovery != null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "recovery_ambiguous:${ambiguousRecovery.first}:${ambiguousRecovery.second}"
            )
        }

        if (observedTreeLabelCounts[playbook.accountProofLabel] == null) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "account_proof_missing:${playbook.accountProofLabel}"
            )
        }

        if ((observedTreeLabelCounts[playbook.accountProofLabel] ?: 0) > 1) {
            return AppAgentPlaybookDryRun(
                verified = false,
                reason = "account_proof_ambiguous:${playbook.accountProofLabel}"
            )
        }

        auditLedger.record("app_agent.playbook_dry_run_verified", playbook.id)
        return AppAgentPlaybookDryRun(
            verified = true,
            reason = "observed_accessibility_tree_dry_run",
            checkedTargets = playbook.steps.flatMap { step ->
                listOf(step.selectorHint, step.expectedState) + step.recoverySelectorHints
            }.toSet() +
                playbook.accountProofLabel
        )
    }
}

fun AppOperationPlaybook.requiresExactApprovalRisk(): Boolean =
    actionType == "public_post.create" ||
        riskLabel.contains("public") ||
        riskLabel.startsWith("high_")

data class AppAgentOnboardingResult(
    val status: AppOperationStatus,
    val detail: String,
    val dryRun: AppAgentPlaybookDryRun? = null
)

data class AppAgentPlaybookDryRun(
    val verified: Boolean,
    val reason: String,
    val checkedTargets: Set<String> = emptySet()
)
