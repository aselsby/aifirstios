package app.conductor.ui

import app.conductor.connectors.ConnectedAccount
import app.conductor.operator.accessibility.AppAgentDiscovery
import app.conductor.operator.accessibility.AppOperationQueueItem
import app.conductor.operator.accessibility.AppOperationPlaybook
import app.conductor.operator.accessibility.AppOperationSession
import app.conductor.operator.accessibility.AppOperationStep
import app.conductor.operator.accessibility.AppLoginState
import app.conductor.graph.AppAgentGrant
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.SystemClock
import app.conductor.runtime.ApprovalStatus
import app.conductor.runtime.AuditEvent
import app.conductor.runtime.ContextItem
import app.conductor.runtime.OperationTimeline
import app.conductor.runtime.PlanStep
import app.conductor.runtime.StepStatus
import app.conductor.runtime.ToolResult
import app.conductor.runtime.WorkflowResult

data class LauncherUiState(
    val title: String,
    val autonomyMode: AutonomyMode,
    val intentType: String,
    val goal: String,
    val transcript: String,
    val recommendationTitle: String,
    val recommendationScore: Int,
    val appSessions: List<AppSessionUi>,
    val appDiscoveries: List<AppDiscoveryUi>,
    val appTeachDraft: AppTeachDraftUi,
    val appSkills: List<AppSkillUi>,
    val appCapabilities: List<AppCapabilityUi>,
    val dataGrants: List<DataGrantUi>,
    val connectorAccounts: List<ConnectorAccountUi>,
    val appAgentGrants: List<AppAgentGrantUi>,
    val sourceFreshness: List<SourceFreshnessUi>,
    val contextCards: List<ContextCardUi>,
    val planSteps: List<PlanStepUi>,
    val approvals: List<ApprovalUi>,
    val appHandoffs: List<AppHandoffUi>,
    val operationTimelines: List<OperationTimelineUi>,
    val appReceipts: List<AppReceiptUi>,
    val auditEvents: List<String>
)

data class ContextCardUi(
    val source: String,
    val type: String,
    val summary: String,
    val accountId: String,
    val allowedPurpose: String,
    val freshnessStatus: String,
    val baseGrantId: String,
    val appAgentGrantId: String
)

data class PlanStepUi(
    val id: String,
    val title: String,
    val tool: String,
    val actionType: String,
    val risk: String,
    val externalSideEffect: Boolean,
    val requiredSourceIds: List<String>,
    val inputSummary: List<String>,
    val policyReason: String,
    val status: StepStatus
)

data class ApprovalUi(
    val id: String,
    val actionType: String,
    val exactContent: String,
    val stepSummaries: List<String>,
    val reason: String,
    val status: ApprovalStatus
)

data class AppHandoffUi(
    val requestId: String,
    val packageName: String,
    val playbookId: String,
    val requiredSourceIds: List<String>,
    val blockedBaseSourceIds: List<String>,
    val inputSummary: List<String>,
    val missingInputKeys: List<String>,
    val exactContentPreview: String,
    val stepSummaries: List<String>,
    val reason: String,
    val requiredUserAction: String,
    val primaryActionLabel: String,
    val autonomyContext: String,
    val createdAtIso: String,
    val expiresAtIso: String
)

data class AppSessionUi(
    val userId: String,
    val packageName: String,
    val loginState: String,
    val loginProofMethod: String,
    val loginProofSubject: String,
    val loginProofVerifiedAtIso: String,
    val autonomyMode: AutonomyMode,
    val allowedPlaybookIds: List<String>,
    val allowedSourceIds: List<String>,
    val supportedActionTypes: List<String>,
    val approvalRequiredActionTypes: List<String>,
    val remainingAutonomousActions: Int,
    val revoked: Boolean,
    val expiresAtIso: String
)

data class AppDiscoveryUi(
    val packageName: String,
    val observedAtIso: String,
    val visibleLabels: List<String>,
    val accountProofCandidates: List<String>,
    val bounded: Boolean
)

data class AppTeachDraftUi(
    val packageName: String,
    val observedAtIso: String,
    val actionType: String,
    val inputKey: String,
    val targetLabel: String,
    val fieldBindingsText: String,
    val fieldBindingSummaries: List<String>,
    val unknownFieldBindingLabels: List<String>,
    val clickLabel: String,
    val clickVerifierLabel: String,
    val recoveryLabelsText: String,
    val selectedRecoveryLabels: List<String>,
    val accountProofLabel: String,
    val riskLabel: String,
    val exactApprovalRequired: Boolean,
    val sourceScopeText: String,
    val availableSourceIds: List<String>,
    val unknownSourceIds: List<String>,
    val selectedSourceIds: List<String>,
    val availableLabels: List<String>,
    val accountProofCandidates: List<String>,
    val stepPreview: List<String>,
    val canSubmit: Boolean,
    val status: String
)

data class AppCapabilityUi(
    val packageName: String,
    val playbookId: String,
    val actionType: String,
    val riskLabel: String,
    val invocationPhrases: List<String>,
    val exactApprovalRequired: Boolean,
    val playbookGrantActive: Boolean,
    val requiredInputKeys: List<String>,
    val requiredSourceIds: List<String>,
    val stepSummaries: List<String>,
    val status: String,
    val reason: String
)

data class AppSkillUi(
    val packageName: String,
    val actionCount: Int,
    val enabledActionCount: Int,
    val readyActionCount: Int,
    val blockedReasons: List<String>,
    val requiredSourceIds: List<String>,
    val requiredInputKeys: List<String>,
    val invocationPhrases: List<String>,
    val capabilities: List<AppCapabilityUi>
)

data class AppReceiptUi(
    val eventType: String,
    val requestId: String,
    val detail: String,
    val verified: Boolean,
    val packageName: String,
    val playbookId: String,
    val actionType: String,
    val accountProof: String,
    val sourceScope: List<String>,
    val inputSummary: List<String>,
    val stepSummaries: List<String>,
    val exactApproval: String,
    val autonomy: String
)

data class OperationTimelineUi(
    val taskId: String,
    val intentType: String,
    val autonomyMode: String,
    val updatedAtIso: String,
    val events: List<OperationTimelineEventUi>
)

data class OperationTimelineEventUi(
    val actionType: String,
    val tool: String,
    val status: String,
    val policyDecision: String,
    val approvalId: String,
    val packageName: String,
    val playbookId: String,
    val sourceScope: List<String>,
    val summary: String
)

data class DataGrantUi(
    val id: String,
    val source: String,
    val accountId: String,
    val purposes: List<String>,
    val revoked: Boolean
)

data class ConnectorAccountUi(
    val source: String,
    val accountId: String,
    val purposes: List<String>,
    val connected: Boolean
)

data class AppAgentGrantUi(
    val id: String,
    val appAgentId: String,
    val packageName: String,
    val purposes: List<String>,
    val sources: List<String>,
    val revoked: Boolean
)

data class SourceFreshnessUi(
    val source: String,
    val status: String,
    val factExpiresAtIso: String,
    val grantExpiresAtIso: String,
    val summary: String
)

fun WorkflowResult.toLauncherUiState(
    queuedAppOperations: List<AppOperationQueueItem> = emptyList(),
    appOperationSessions: List<AppOperationSession> = emptyList(),
    appAgentDiscoveries: List<AppAgentDiscovery> = emptyList(),
    graphGrants: List<GraphGrant> = emptyList(),
    graphFacts: List<GraphFact> = emptyList(),
    connectorAccounts: List<ConnectedAccount> = emptyList(),
    appAgentGrants: List<AppAgentGrant> = emptyList(),
    operationTimelines: List<OperationTimeline> = emptyList(),
    durableAuditEvents: List<AuditEvent> = emptyList(),
    appOperationPlaybooks: List<AppOperationPlaybook> = emptyList(),
    teachDraftActionType: String = "",
    teachDraftInputKey: String = "",
    teachDraftTargetLabel: String = "",
    teachDraftFieldBindingsText: String = "",
    teachDraftClickLabel: String = "",
    teachDraftClickVerifierLabel: String = "",
    teachDraftRecoveryLabelsText: String = "",
    teachDraftAccountProofLabel: String = "",
    teachDraftRiskLabel: String = "",
    teachDraftSourceScopeText: String = ""
): LauncherUiState {
    val resultsByStep = firstPassResults.associateBy { it.stepId }
    val combinedAudit = (durableAuditEvents + audit).distinctBy { "${it.type}:${it.detail}" }
    val appCapabilities = appOperationPlaybooks.toAppCapabilityUi(
        sessions = appOperationSessions,
        graphGrants = graphGrants,
        appAgentGrants = appAgentGrants
    )
    return LauncherUiState(
        title = "Conductor OS",
        autonomyMode = task.mode,
        intentType = task.intentType,
        goal = task.goal,
        transcript = task.goal,
        recommendationTitle = plan.recommendation.title,
        recommendationScore = plan.recommendation.score,
        appSessions = appOperationSessions.map { it.toAppSessionUi(appOperationPlaybooks) },
        appDiscoveries = appAgentDiscoveries.map { it.toAppDiscoveryUi() },
        appTeachDraft = appAgentDiscoveries.toAppTeachDraftUi(
            actionType = teachDraftActionType,
            inputKey = teachDraftInputKey,
            targetLabel = teachDraftTargetLabel,
            fieldBindingsText = teachDraftFieldBindingsText,
            clickLabel = teachDraftClickLabel,
            clickVerifierLabel = teachDraftClickVerifierLabel,
            recoveryLabelsText = teachDraftRecoveryLabelsText,
            accountProofLabel = teachDraftAccountProofLabel,
            riskLabel = teachDraftRiskLabel,
            sourceScopeText = teachDraftSourceScopeText,
            availableSourceIds = (graphGrants.map { it.source } + connectorAccounts.map { it.source }).toSet().sorted()
        ),
        appSkills = appCapabilities.toAppSkillUi(),
        appCapabilities = appCapabilities,
        dataGrants = graphGrants.map { it.toDataGrantUi() },
        connectorAccounts = connectorAccounts.map { it.toConnectorAccountUi() },
        appAgentGrants = appAgentGrants.map { it.toAppAgentGrantUi() },
        sourceFreshness = graphFacts.toSourceFreshnessUi(graphGrants, connectorAccounts),
        contextCards = context.items.map { (source, item) -> item.toContextCardUi(source) },
        planSteps = plan.steps.map { step -> step.toPlanStepUi(resultsByStep[step.id]) },
        approvals = firstPassResults.mapNotNull { result ->
            result.approval?.let { approval ->
                ApprovalUi(
                    id = approval.id,
                    actionType = approval.actionType,
                    exactContent = approval.exactContent.orEmpty(),
                    stepSummaries = appOperationPlaybooks
                        .firstOrNull { it.actionType == approval.actionType }
                        ?.steps
                        ?.map { it.toStepSummary() }
                        .orEmpty(),
                    reason = approval.reason,
                    status = approval.status
                )
            }
        },
        appHandoffs = queuedAppOperations.map { it.toAppHandoffUi(graphGrants, appOperationPlaybooks) },
        operationTimelines = operationTimelines.toOperationTimelineUi(task.id),
        appReceipts = combinedAudit.toAppReceiptUi(),
        auditEvents = combinedAudit.map { "${it.type}: ${it.detail}" }
    )
}

private fun List<OperationTimeline>.toOperationTimelineUi(currentTaskId: String): List<OperationTimelineUi> =
    sortedWith(compareByDescending<OperationTimeline> { it.taskId == currentTaskId }.thenByDescending { it.updatedAtIso })
        .take(3)
        .map { timeline ->
            OperationTimelineUi(
                taskId = timeline.taskId,
                intentType = timeline.intentType,
                autonomyMode = timeline.autonomyMode.name,
                updatedAtIso = timeline.updatedAtIso,
                events = timeline.events.map { event ->
                    OperationTimelineEventUi(
                        actionType = event.actionType,
                        tool = event.tool,
                        status = event.status,
                        policyDecision = event.policyDecision,
                        approvalId = event.approvalId,
                        packageName = event.packageName,
                        playbookId = event.playbookId,
                        sourceScope = event.sourceScope,
                        summary = event.summary
                    )
                }
            )
        }

private fun ContextItem.toContextCardUi(sourceName: String): ContextCardUi =
    ContextCardUi(
        source = sourceName,
        type = type,
        summary = summary,
        accountId = accountId,
        allowedPurpose = allowedPurpose,
        freshnessStatus = freshnessStatus,
        baseGrantId = baseGrantId,
        appAgentGrantId = appAgentGrantId
    )

private fun PlanStep.toPlanStepUi(result: ToolResult?): PlanStepUi =
    PlanStepUi(
        id = id,
        title = title,
        tool = tool,
        actionType = actionType,
        risk = risk.name,
        externalSideEffect = externalSideEffect,
        requiredSourceIds = input["__requiredSourceIds"]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.sorted()
            .orEmpty(),
        inputSummary = input.toInputSummary(),
        policyReason = result?.policy?.reason.orEmpty(),
        status = result?.status ?: StepStatus.FAILED
    )

private fun AppOperationQueueItem.toAppHandoffUi(
    graphGrants: List<GraphGrant>,
    appOperationPlaybooks: List<AppOperationPlaybook>
): AppHandoffUi =
    AppHandoffUi(
        requestId = request.id,
        packageName = request.packageName,
        playbookId = request.playbookId,
        requiredSourceIds = request.requiredSourceIds.toList().sorted(),
        blockedBaseSourceIds = request.requiredSourceIds
            .filter { sourceId ->
                graphGrants.none {
                    !it.revoked &&
                        !it.isExpired(SystemClock.nowIso()) &&
                        it.source == sourceId &&
                        it.purposes.contains("activity_planning")
                }
            }
            .sorted(),
        inputSummary = request.input.toInputSummary(),
        missingInputKeys = reason.missingInputKeys(),
        exactContentPreview = request.input.exactApprovalContent(),
        stepSummaries = appOperationPlaybooks
            .firstOrNull { it.id == request.playbookId && it.packageName == request.packageName }
            ?.steps
            ?.map { it.toStepSummary() }
            .orEmpty(),
        reason = reason,
        requiredUserAction = requiredUserAction,
        primaryActionLabel = primaryActionLabel,
        autonomyContext = autonomyContext,
        createdAtIso = createdAtIso,
        expiresAtIso = expiresAtIso
    )

private fun AppAgentDiscovery.toAppDiscoveryUi(): AppDiscoveryUi =
    AppDiscoveryUi(
        packageName = packageName,
        observedAtIso = observedAtIso,
        visibleLabels = visibleLabelCounts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { "${it.key} (${it.value})" },
        accountProofCandidates = accountProofCandidates.toList().sorted(),
        bounded = bounded
    )

private fun List<AppAgentDiscovery>.toAppTeachDraftUi(
    actionType: String,
    inputKey: String,
    targetLabel: String,
    fieldBindingsText: String,
    clickLabel: String,
    clickVerifierLabel: String,
    recoveryLabelsText: String,
    accountProofLabel: String,
    riskLabel: String,
    sourceScopeText: String,
    availableSourceIds: List<String>
): AppTeachDraftUi {
    val discovery = sortedByDescending { it.observedAtIso }.firstOrNull()
        ?: return AppTeachDraftUi(
            packageName = "",
            observedAtIso = "",
            actionType = actionType.ifBlank { "tasks.add" },
            inputKey = inputKey.ifBlank { "title" },
            targetLabel = targetLabel,
            fieldBindingsText = fieldBindingsText,
            fieldBindingSummaries = fieldBindingsText.toFieldBindings().map { "${it.first}=${it.second}" },
            unknownFieldBindingLabels = fieldBindingsText.toFieldBindings().map { it.first },
            clickLabel = clickLabel,
            clickVerifierLabel = clickVerifierLabel,
            recoveryLabelsText = recoveryLabelsText,
            selectedRecoveryLabels = recoveryLabelsText.toLabelSet().toList().sorted(),
            accountProofLabel = accountProofLabel,
            riskLabel = riskLabel.ifBlank { "low_reversible" },
            exactApprovalRequired = riskLabel.requiresExactApprovalRisk(),
            sourceScopeText = sourceScopeText,
            availableSourceIds = availableSourceIds,
            unknownSourceIds = sourceScopeText.toSourceIds() - availableSourceIds.toSet(),
            selectedSourceIds = sourceScopeText.toSourceIds().toList().sorted(),
            availableLabels = emptyList(),
            accountProofCandidates = emptyList(),
            stepPreview = emptyList(),
            canSubmit = false,
            status = "Observe an app before authoring a playbook."
        )
    val uniqueLabels = discovery.visibleLabelCounts.entries
        .filter { it.value == 1 }
        .map { it.key }
        .sorted()
    val proof = accountProofLabel.ifBlank { discovery.accountProofCandidates.firstOrNull().orEmpty() }
    val target = targetLabel.ifBlank {
        uniqueLabels.firstOrNull { !discovery.accountProofCandidates.contains(it) }.orEmpty()
    }
    val authoredActionType = actionType.ifBlank { "app_agent.observed.${discovery.packageName}" }
    val authoredInputKey = inputKey.ifBlank { "title" }
    val authoredRiskLabel = riskLabel.ifBlank { "low_reversible" }
    val exactApprovalRequired = authoredRiskLabel.requiresExactApprovalRisk() ||
        authoredActionType == "public_post.create" ||
        authoredActionType.contains("public")
    val requestedSourceIds = sourceScopeText.toSourceIds()
    val unknownSourceIds = requestedSourceIds - availableSourceIds.toSet()
    val fieldBindings = fieldBindingsText.toFieldBindings()
    val unknownFieldBindingLabels = fieldBindings
        .map { it.first }
        .filterNot { uniqueLabels.contains(it) }
        .sorted()
    val clickVerifier = clickVerifierLabel.ifBlank { clickLabel }
    val recoveryLabels = recoveryLabelsText.toLabelSet()
    val fieldBindingPreviews = fieldBindings.map { (label, key) ->
        "set_text target=$label input=$key verify=$label"
    }
    val stepPreview = listOfNotNull(
        "set_text target=$target input=$authoredInputKey verify=$target" +
            recoveryLabels.takeIf { it.isNotEmpty() }?.let { " recover=${it.sorted().joinToString("|")}" }.orEmpty(),
        *fieldBindingPreviews.toTypedArray(),
        clickLabel.takeIf { it.isNotBlank() }?.let { "click target=$it verify=$clickVerifier" }
    )
    return AppTeachDraftUi(
        packageName = discovery.packageName,
        observedAtIso = discovery.observedAtIso,
        actionType = authoredActionType,
        inputKey = authoredInputKey,
        targetLabel = target,
        fieldBindingsText = fieldBindingsText,
        fieldBindingSummaries = fieldBindings.map { "${it.first}=${it.second}" },
        unknownFieldBindingLabels = unknownFieldBindingLabels,
        clickLabel = clickLabel,
        clickVerifierLabel = clickVerifier,
        recoveryLabelsText = recoveryLabelsText,
        selectedRecoveryLabels = recoveryLabels.toList().sorted(),
        accountProofLabel = proof,
        riskLabel = authoredRiskLabel,
        exactApprovalRequired = exactApprovalRequired,
        sourceScopeText = sourceScopeText,
        availableSourceIds = availableSourceIds,
        unknownSourceIds = unknownSourceIds.sorted(),
        selectedSourceIds = requestedSourceIds.toList().sorted(),
        availableLabels = uniqueLabels,
        accountProofCandidates = discovery.accountProofCandidates.toList().sorted(),
        stepPreview = stepPreview,
        canSubmit = proof.isNotBlank() &&
            target.isNotBlank() &&
            authoredActionType.isNotBlank() &&
            authoredInputKey.isNotBlank() &&
            unknownSourceIds.isEmpty() &&
            unknownFieldBindingLabels.isEmpty(),
        status = when {
            proof.isBlank() || target.isBlank() -> "Pick a unique account proof and target from the observed app."
            unknownSourceIds.isNotEmpty() -> "Unknown sources: ${unknownSourceIds.sorted().joinToString()}."
            unknownFieldBindingLabels.isNotEmpty() -> "Unknown field labels: ${unknownFieldBindingLabels.joinToString()}."
            else -> "Ready to dry-run against the observed app tree."
        }
    )
}

private fun String.toSourceIds(): Set<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.toLabelSet(): Set<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.toFieldBindings(): List<Pair<String, String>> =
    split(";")
        .mapNotNull { binding ->
            val parts = binding.split("=", limit = 2)
            val label = parts.getOrNull(0)?.trim().orEmpty()
            val inputKey = parts.getOrNull(1)?.trim().orEmpty()
            if (label.isBlank() || inputKey.isBlank()) null else label to inputKey
        }

private fun String.requiresExactApprovalRisk(): Boolean =
    contains("public") ||
        startsWith("high_")

private fun Map<String, String>.toInputSummary(): List<String> =
    entries
        .filter { it.key != "exactBody" }
        .sortedBy { it.key }
        .map { "${it.key}: ${it.value}" }

private fun Map<String, String>.exactApprovalContent(): String =
    get("exactBody")
        ?: get("body")
        ?: get("title")
        ?: toInputSummary().joinToString()

private fun String.missingInputKeys(): List<String> =
    substringAfter("Missing or ambiguous input: ", "")
        .takeIf { it.isNotBlank() }
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.sorted()
        ?: emptyList()

private fun AppOperationSession.toAppSessionUi(playbooks: List<AppOperationPlaybook>): AppSessionUi =
    AppSessionUi(
        userId = userId,
        packageName = packageName,
        loginState = loginState.name,
        loginProofMethod = loginProof.method,
        loginProofSubject = loginProof.subjectLabel,
        loginProofVerifiedAtIso = loginProof.verifiedAtIso,
        autonomyMode = autonomyMode,
        allowedPlaybookIds = allowedPlaybookIds.toList().sorted(),
        allowedSourceIds = allowedSourceIds.toList().sorted(),
        supportedActionTypes = playbooks
            .filter { it.packageName == packageName && allows(it.id) }
            .map { it.actionType }
            .distinct()
            .sorted(),
        approvalRequiredActionTypes = approvalRequiredActionTypes.toList().sorted(),
        remainingAutonomousActions = remainingAutonomousActions,
        revoked = revoked,
        expiresAtIso = expiresAtIso
    )

private fun List<AppOperationPlaybook>.toAppCapabilityUi(
    sessions: List<AppOperationSession>,
    graphGrants: List<GraphGrant>,
    appAgentGrants: List<AppAgentGrant>
): List<AppCapabilityUi> {
    val nowIso = SystemClock.nowIso()
    val activeBaseSourceIds = graphGrants
        .filter { !it.revoked && !it.isExpired(nowIso) && it.purposes.contains("activity_planning") }
        .map { it.source }
        .toSet()
    return sortedWith(compareBy<AppOperationPlaybook> { it.packageName }.thenBy { it.actionType })
        .map { playbook ->
            val session = sessions.firstOrNull { it.packageName == playbook.packageName && !it.revoked }
            val activeAppAgentSourceIds = appAgentGrants
                .filter {
                    !it.revoked &&
                        !it.isExpired(nowIso) &&
                        it.packageName == playbook.packageName &&
                        it.appAgentId == "conductor.voice" &&
                        it.purposes.contains("activity_planning")
                }
                .flatMap { it.sources }
                .toSet()
            val missingBaseSources = playbook.requiredSourceIds - activeBaseSourceIds
            val missingAgentSources = playbook.requiredSourceIds - activeAppAgentSourceIds
            val sessionApprovalOverride = session?.requiresApprovalFor(playbook.actionType) == true
            val exactApprovalRequired = playbook.requiresExactApproval || sessionApprovalOverride
            val statusReason = when {
                session == null -> "Needs login" to "No active logged-in app session."
                session.loginState != AppLoginState.LOGGED_IN || !session.hasLoginProof() || session.isExpired(nowIso) ->
                    "Needs login" to "Login proof is missing, expired, or logged out."
                !session.allows(playbook.id) ->
                    "Needs app grant" to "Session has not granted ${playbook.id}."
                missingBaseSources.isNotEmpty() ->
                    "Needs data" to "Restore base sources: ${missingBaseSources.sorted().joinToString()}."
                missingAgentSources.isNotEmpty() ->
                    "Needs agent data" to "Grant this app-agent sources: ${missingAgentSources.sorted().joinToString()}."
                session.autonomyMode == AutonomyMode.ASK_ONLY ->
                    "Manual only" to "Ready to hand off after user approval."
                sessionApprovalOverride ->
                    "Ready with approval" to "Session requires exact approval for ${playbook.actionType}."
                else ->
                    "Ready" to "Can route under ${session.autonomyMode}."
            }
            AppCapabilityUi(
                packageName = playbook.packageName,
                playbookId = playbook.id,
                actionType = playbook.actionType,
                riskLabel = playbook.riskLabel,
                invocationPhrases = playbook.invocationPhrases.toList().sorted(),
                exactApprovalRequired = exactApprovalRequired,
                playbookGrantActive = session?.allows(playbook.id) == true,
                requiredInputKeys = playbook.requiredInputKeys.toList().sorted(),
                requiredSourceIds = playbook.requiredSourceIds.toList().sorted(),
                stepSummaries = playbook.steps.map { it.toStepSummary() },
                status = statusReason.first,
                reason = statusReason.second
            )
        }
}

private fun List<AppCapabilityUi>.toAppSkillUi(): List<AppSkillUi> =
    groupBy { it.packageName }
        .toSortedMap()
        .map { (packageName, capabilities) ->
            val sortedCapabilities = capabilities.sortedBy { it.actionType }
            AppSkillUi(
                packageName = packageName,
                actionCount = sortedCapabilities.size,
                enabledActionCount = sortedCapabilities.count { it.playbookGrantActive },
                readyActionCount = sortedCapabilities.count {
                    it.status == "Ready" || it.status == "Ready with approval" || it.status == "Manual only"
                },
                blockedReasons = sortedCapabilities
                    .filterNot { it.status == "Ready" || it.status == "Ready with approval" || it.status == "Manual only" }
                    .map { "${it.actionType}: ${it.status}" }
                    .distinct()
                    .sorted(),
                requiredSourceIds = sortedCapabilities
                    .flatMap { it.requiredSourceIds }
                    .distinct()
                    .sorted(),
                requiredInputKeys = sortedCapabilities
                    .flatMap { it.requiredInputKeys }
                    .distinct()
                    .sorted(),
                invocationPhrases = sortedCapabilities
                    .flatMap { it.invocationPhrases }
                    .distinct()
                    .sorted(),
                capabilities = sortedCapabilities
            )
        }

private fun AppOperationStep.toStepSummary(): String =
    listOfNotNull(
        operation.takeIf { it.isNotBlank() },
        selectorHint.takeIf { it.isNotBlank() }?.let { "target=$it" },
        inputKey.takeIf { it.isNotBlank() }?.let { "input=$it" },
        expectedState.takeIf { it.isNotBlank() }?.let { "verify=$it" },
        recoverySelectorHints.takeIf { it.isNotEmpty() }?.let { "recover=${it.joinToString("|")}" }
    ).joinToString(" ")

private fun GraphGrant.toDataGrantUi(): DataGrantUi =
    DataGrantUi(
        id = id,
        source = source,
        accountId = accountId,
        purposes = purposes.toList().sorted(),
        revoked = revoked
    )

private fun ConnectedAccount.toConnectorAccountUi(): ConnectorAccountUi =
    ConnectorAccountUi(
        source = source,
        accountId = accountId,
        purposes = purposes.toList().sorted(),
        connected = credentialHandle.isNotBlank()
    )

private fun AppAgentGrant.toAppAgentGrantUi(): AppAgentGrantUi =
    AppAgentGrantUi(
        id = id,
        appAgentId = appAgentId,
        packageName = packageName,
        purposes = purposes.toList().sorted(),
        sources = sources.toList().sorted(),
        revoked = revoked
    )

private fun List<GraphFact>.toSourceFreshnessUi(
    graphGrants: List<GraphGrant>,
    connectorAccounts: List<ConnectedAccount>
): List<SourceFreshnessUi> {
    val nowIso = SystemClock.nowIso()
    val sources = (map { it.source } + graphGrants.map { it.source } + connectorAccounts.map { it.source }).toSet().sorted()
    return sources.map { source ->
        val newestFact = filter { it.source == source }
            .sortedByDescending { it.expiresAtIso.orEmpty() }
            .firstOrNull()
        val connectorAccount = connectorAccounts.firstOrNull {
            it.source == source && it.purposes.contains("activity_planning")
        }
        val grant = graphGrants
            .filter { it.source == source && it.purposes.contains("activity_planning") }
            .sortedByDescending { it.expiresAtIso.orEmpty() }
            .firstOrNull()
        val status = when {
            connectorAccount == null -> "Connector missing"
            connectorAccount.credentialHandle.isBlank() -> "Credential missing"
            grant == null -> "Grant missing"
            grant.revoked -> "Grant revoked"
            grant.isExpired(nowIso) -> "Grant expired"
            newestFact == null -> "No active fact"
            newestFact.isExpired(nowIso) -> "Fact expired"
            !newestFact.allowedPurposes.contains("activity_planning") -> "Purpose blocked"
            else -> "Fresh"
        }
        SourceFreshnessUi(
            source = source,
            status = status,
            factExpiresAtIso = newestFact?.expiresAtIso ?: "none",
            grantExpiresAtIso = grant?.expiresAtIso ?: "none",
            summary = newestFact?.redactedSummary ?: newestFact?.summary ?: "No retained fact for activity planning."
        )
    }
}

private fun List<AuditEvent>.toAppReceiptUi(): List<AppReceiptUi> {
    val previewByRequestId = filter { it.type == "operator.execution_preview" }
        .associate { event -> event.detail.receiptRequestId() to event.detail.toExecutionPreviewParts() }

    return filter {
            it.type == "operator.execution_preview" ||
            it.type == "operator.live_verified" ||
            it.type == "operator.verified" ||
            it.type == "operator.source_scope_verified" ||
            it.type == "operator.approval_consumed" ||
            it.type == "accessibility.queue_resolved"
    }
        .takeLast(12)
        .reversed()
        .map { event ->
            val requestId = event.detail.receiptRequestId()
            val preview = event.detail.toExecutionPreviewParts()
                .takeIf { it.hasReviewContext() }
                ?: previewByRequestId[requestId]
                ?: ExecutionPreviewParts()
            AppReceiptUi(
                eventType = event.type,
                requestId = requestId,
                detail = event.detail,
                verified = event.type != "operator.execution_preview" &&
                    (
                        event.type == "operator.live_verified" ||
                            event.type == "operator.verified" ||
                            event.type == "operator.source_scope_verified" ||
                            event.type == "accessibility.queue_resolved"
                    ),
                packageName = preview.packageName,
                playbookId = preview.playbookId,
                actionType = preview.actionType,
                accountProof = preview.accountProof,
                sourceScope = preview.sourceScope,
                inputSummary = preview.inputSummary,
                stepSummaries = preview.stepSummaries,
                exactApproval = preview.exactApproval,
                autonomy = preview.autonomy
            )
        }
}

private data class ExecutionPreviewParts(
    val packageName: String = "",
    val playbookId: String = "",
    val actionType: String = "",
    val accountProof: String = "",
    val sourceScope: List<String> = emptyList(),
    val inputSummary: List<String> = emptyList(),
    val stepSummaries: List<String> = emptyList(),
    val exactApproval: String = "",
    val autonomy: String = ""
)

private fun ExecutionPreviewParts.hasReviewContext(): Boolean =
    packageName.isNotBlank() ||
        playbookId.isNotBlank() ||
        actionType.isNotBlank() ||
        stepSummaries.isNotEmpty()

private fun String.toExecutionPreviewParts(): ExecutionPreviewParts =
    ExecutionPreviewParts(
        packageName = previewValue("app", listOf("playbook")),
        playbookId = previewValue("playbook", listOf("action")),
        actionType = previewValue("action", listOf("accountProof")),
        accountProof = previewValue("accountProof", listOf("sources")),
        sourceScope = previewValue("sources", listOf("inputs")).commaList(),
        inputSummary = previewValue("inputs", listOf("steps")).commaList(),
        stepSummaries = previewValue("steps", listOf("exactApproval")).pipeList(),
        exactApproval = previewValue("exactApproval", listOf("autonomy")),
        autonomy = previewValue("autonomy", emptyList())
    )

private fun String.previewValue(key: String, nextKeys: List<String>): String {
    val token = "$key="
    val valueStart = indexOf(token).takeIf { it >= 0 }?.plus(token.length) ?: return ""
    val valueEnd = nextKeys
        .mapNotNull { nextKey ->
            indexOf(" $nextKey=", startIndex = valueStart).takeIf { it >= 0 }
        }
        .minOrNull() ?: length
    return substring(valueStart, valueEnd).trim()
}

private fun String.commaList(): List<String> =
    split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun String.pipeList(): List<String> =
    split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }

private fun String.receiptRequestId(): String =
    substringBefore(" ").substringBefore(":")
