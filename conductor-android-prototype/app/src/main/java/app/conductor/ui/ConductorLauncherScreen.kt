package app.conductor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.ApprovalStatus
import app.conductor.runtime.StepStatus

@Composable
fun ConductorLauncherScreen(
    state: LauncherUiState,
    onVoicePressed: () -> Unit,
    onStopAutonomy: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit = {},
    onAutonomySelected: (AutonomyMode) -> Unit,
    onAppSessionAutonomySelected: (String, AutonomyMode) -> Unit,
    onAppSessionApprovalOverrideToggled: (String, String) -> Unit,
    onAppSessionRevoked: (String) -> Unit,
    onAppPlaybookGrantToggled: (String, String, Boolean) -> Unit,
    onTeachDraftActionTypeChanged: (String) -> Unit,
    onTeachDraftInputKeyChanged: (String) -> Unit,
    onTeachDraftTargetLabelChanged: (String) -> Unit,
    onTeachDraftFieldBindingsChanged: (String) -> Unit,
    onTeachDraftClickLabelChanged: (String) -> Unit,
    onTeachDraftClickVerifierChanged: (String) -> Unit,
    onTeachDraftRecoveryLabelsChanged: (String) -> Unit,
    onTeachDraftAccountProofChanged: (String) -> Unit,
    onTeachDraftRiskChanged: (String) -> Unit,
    onTeachDraftSourceScopeChanged: (String) -> Unit,
    onTeachAppAgent: () -> Unit,
    onDataGrantRevoked: (String) -> Unit,
    onDataGrantRestored: (String) -> Unit,
    onSourceRefresh: (String) -> Unit,
    onAppAgentGrantRevoked: (String) -> Unit,
    onAppAgentGrantRestored: (String) -> Unit,
    onApprovalApproved: (String) -> Unit,
    onApprovalDenied: (String) -> Unit,
    onAppHandoffGranted: (String) -> Unit,
    onAppHandoffCancelled: (String) -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF7F4EF)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HeroVoicePanel(state = state, onVoicePressed = onVoicePressed)
                EmergencyStopRow(onStopAutonomy = onStopAutonomy)
                SetupRow(onOpenAccessibilitySettings = onOpenAccessibilitySettings)
                AutonomyModeRow(selected = state.autonomyMode, onAutonomySelected = onAutonomySelected)
                // Hero outcome first: plan before app-management chrome.
                RecommendationCard(state)
                ContextSection(state.contextCards)
                PlanSection(state.planSteps)
                ApprovalSection(
                    approvals = state.approvals,
                    onApprovalApproved = onApprovalApproved,
                    onApprovalDenied = onApprovalDenied
                )
                AppHandoffSection(
                    handoffs = state.appHandoffs,
                    onAppHandoffGranted = onAppHandoffGranted,
                    onAppHandoffCancelled = onAppHandoffCancelled
                )
                OperationTimelineSection(state.operationTimelines)
                AppReceiptSection(state.appReceipts)
                SourceFreshnessSection(
                    freshness = state.sourceFreshness,
                    onSourceRefresh = onSourceRefresh
                )
                DataAccessSection(
                    grants = state.dataGrants,
                    connectorAccounts = state.connectorAccounts,
                    onDataGrantRevoked = onDataGrantRevoked,
                    onDataGrantRestored = onDataGrantRestored
                )
                AppAgentAccessSection(
                    grants = state.appAgentGrants,
                    onAppAgentGrantRevoked = onAppAgentGrantRevoked,
                    onAppAgentGrantRestored = onAppAgentGrantRestored
                )
                AppCapabilitySection(
                    appSkills = state.appSkills,
                    onAppPlaybookGrantToggled = onAppPlaybookGrantToggled
                )
                AppSessionSection(
                    sessions = state.appSessions,
                    teachDraft = state.appTeachDraft,
                    onAppSessionAutonomySelected = onAppSessionAutonomySelected,
                    onAppSessionApprovalOverrideToggled = onAppSessionApprovalOverrideToggled,
                    onAppSessionRevoked = onAppSessionRevoked,
                    onTeachDraftActionTypeChanged = onTeachDraftActionTypeChanged,
                    onTeachDraftInputKeyChanged = onTeachDraftInputKeyChanged,
                    onTeachDraftTargetLabelChanged = onTeachDraftTargetLabelChanged,
                    onTeachDraftFieldBindingsChanged = onTeachDraftFieldBindingsChanged,
                    onTeachDraftClickLabelChanged = onTeachDraftClickLabelChanged,
                    onTeachDraftClickVerifierChanged = onTeachDraftClickVerifierChanged,
                    onTeachDraftRecoveryLabelsChanged = onTeachDraftRecoveryLabelsChanged,
                    onTeachDraftAccountProofChanged = onTeachDraftAccountProofChanged,
                    onTeachDraftRiskChanged = onTeachDraftRiskChanged,
                    onTeachDraftSourceScopeChanged = onTeachDraftSourceScopeChanged,
                    onTeachAppAgent = onTeachAppAgent
                )
                AppDiscoverySection(discoveries = state.appDiscoveries)
                AuditSection(state.auditEvents)
            }
        }
    }
}

@Composable
private fun AppDiscoverySection(discoveries: List<AppDiscoveryUi>) {
    Section(title = "Observed apps") {
        if (discoveries.isEmpty()) {
            InfoCard(title = "No observed app screens") {
                Text("Open an app with Accessibility enabled to prepare a teachable app-agent snapshot.")
            }
        }
        discoveries.forEach { discovery ->
            InfoCard(title = discovery.packageName) {
                Text("Observed: ${discovery.observedAtIso}", color = Color(0xFF64748B))
                StatusText(if (discovery.bounded) "Bounded snapshot" else "Complete snapshot")
                if (discovery.accountProofCandidates.isNotEmpty()) {
                    Text(
                        "Account proof candidates: ${discovery.accountProofCandidates.joinToString()}",
                        color = Color(0xFF475569)
                    )
                }
                Text(
                    "Visible labels: ${discovery.visibleLabels.take(6).joinToString()}",
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun AppCapabilitySection(
    appSkills: List<AppSkillUi>,
    onAppPlaybookGrantToggled: (String, String, Boolean) -> Unit
) {
    Section(title = "App skills") {
        if (appSkills.isEmpty()) {
            InfoCard(title = "No app skills") {
                Text("Teach or connect an app to expose agent-operable actions.")
            }
        }
        appSkills.forEach { appSkill ->
            InfoCard(title = appSkill.packageName) {
                StatusText("${appSkill.readyActionCount}/${appSkill.actionCount} actions ready")
                Text("Enabled actions: ${appSkill.enabledActionCount}", color = Color(0xFF475569))
                if (appSkill.blockedReasons.isNotEmpty()) {
                    Text("Needs attention: ${appSkill.blockedReasons.joinToString()}", color = Color(0xFF9A5B00))
                }
                if (appSkill.requiredSourceIds.isNotEmpty()) {
                    Text("Sources: ${appSkill.requiredSourceIds.joinToString()}", color = Color(0xFF64748B))
                }
                if (appSkill.requiredInputKeys.isNotEmpty()) {
                    Text("Inputs: ${appSkill.requiredInputKeys.joinToString()}", color = Color(0xFF64748B))
                }
                if (appSkill.invocationPhrases.isNotEmpty()) {
                    Text("Say: ${appSkill.invocationPhrases.take(6).joinToString()}", color = Color(0xFF64748B))
                }
                appSkill.capabilities.forEach { capability ->
                    AppSkillActionRow(
                        capability = capability,
                        onAppPlaybookGrantToggled = onAppPlaybookGrantToggled
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSkillActionRow(
    capability: AppCapabilityUi,
    onAppPlaybookGrantToggled: (String, String, Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
    ) {
        Text(capability.actionType, fontWeight = FontWeight.SemiBold)
        StatusText(capability.status)
        Text(capability.reason, color = Color(0xFF475569))
        Text("Playbook: ${capability.playbookId}", color = Color(0xFF64748B))
        Text("Risk: ${capability.riskLabel}", color = Color(0xFF64748B))
        Text(
            "Approval: ${if (capability.exactApprovalRequired) "Exact content required" else "Policy based"}",
            color = Color(0xFF64748B)
        )
        Text(
            "Playbook grant: ${if (capability.playbookGrantActive) "Enabled" else "Disabled"}",
            color = Color(0xFF64748B)
        )
        if (capability.stepSummaries.isNotEmpty()) {
            Text("Steps: ${capability.stepSummaries.joinToString(" | ")}", color = Color(0xFF64748B))
        }
        OutlinedButton(
            onClick = {
                onAppPlaybookGrantToggled(
                    capability.packageName,
                    capability.playbookId,
                    !capability.playbookGrantActive
                )
            }
        ) {
            Text(if (capability.playbookGrantActive) "Disable playbook" else "Enable playbook")
        }
    }
}

@Composable
private fun SourceFreshnessSection(
    freshness: List<SourceFreshnessUi>,
    onSourceRefresh: (String) -> Unit
) {
    Section(title = "Source freshness") {
        if (freshness.isEmpty()) {
            InfoCard(title = "No retained sources") {
                Text("Cross-app context freshness will appear after connector hydration.")
            }
        }
        freshness.forEach { source ->
            InfoCard(title = source.source) {
                StatusText(source.status)
                Text("Fact expires: ${source.factExpiresAtIso}", color = Color(0xFF64748B))
                Text("Grant expires: ${source.grantExpiresAtIso}", color = Color(0xFF64748B))
                Text(source.summary, color = Color(0xFF475569))
                OutlinedButton(onClick = { onSourceRefresh(source.source) }) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun AppAgentAccessSection(
    grants: List<AppAgentGrantUi>,
    onAppAgentGrantRevoked: (String) -> Unit,
    onAppAgentGrantRestored: (String) -> Unit
) {
    Section(title = "Agent data access") {
        if (grants.isEmpty()) {
            InfoCard(title = "No agent grants") {
                Text("Agent-specific data grants will appear after Conductor scopes context.")
            }
        }
        grants.forEach { grant ->
            InfoCard(title = grant.appAgentId) {
                Text(grant.packageName, fontWeight = FontWeight.SemiBold)
                Text("Purpose: ${grant.purposes.joinToString()}")
                Text("Sources: ${grant.sources.joinToString()}", color = Color(0xFF64748B))
                StatusText(if (grant.revoked) "Revoked" else "Active")
                if (grant.revoked) {
                    OutlinedButton(onClick = { onAppAgentGrantRestored(grant.id) }) {
                        Text("Restore")
                    }
                } else {
                    OutlinedButton(onClick = { onAppAgentGrantRevoked(grant.id) }) {
                        Text("Revoke")
                    }
                }
            }
        }
    }
}

@Composable
private fun DataAccessSection(
    grants: List<DataGrantUi>,
    connectorAccounts: List<ConnectorAccountUi>,
    onDataGrantRevoked: (String) -> Unit,
    onDataGrantRestored: (String) -> Unit
) {
    Section(title = "Data access") {
        connectorAccounts.forEach { account ->
            InfoCard(title = "Connector ${account.source}") {
                Text(account.accountId, fontWeight = FontWeight.SemiBold)
                Text("Purposes: ${account.purposes.joinToString()}", color = Color(0xFF64748B))
                StatusText(if (account.connected) "Connected" else "Credential missing")
            }
        }
        if (grants.isEmpty()) {
            InfoCard(title = "No connected data") {
                Text("Connectors will appear here after Conductor gathers context.")
            }
        }
        grants.forEach { grant ->
            InfoCard(title = grant.source) {
                Text("${grant.accountId} · ${grant.purposes.joinToString()}")
                StatusText(if (grant.revoked) "Revoked" else "Active")
                if (grant.revoked) {
                    OutlinedButton(onClick = { onDataGrantRestored(grant.id) }) {
                        Text("Restore")
                    }
                } else {
                    OutlinedButton(onClick = { onDataGrantRevoked(grant.id) }) {
                        Text("Revoke")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmergencyStopRow(onStopAutonomy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        OutlinedButton(onClick = onStopAutonomy) {
            Text("Stop autonomy")
        }
    }
}

@Composable
private fun SetupRow(onOpenAccessibilitySettings: () -> Unit) {
    InfoCard(title = "Make apps operable") {
        Text(
            "Enable Conductor Accessibility, then open a logged-in app so live playbooks can run under your autonomy rules.",
            color = Color(0xFF475569)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenAccessibilitySettings, modifier = Modifier.fillMaxWidth()) {
            Text("Open accessibility settings")
        }
    }
}

@Composable
private fun AppSessionSection(
    sessions: List<AppSessionUi>,
    teachDraft: AppTeachDraftUi,
    onAppSessionAutonomySelected: (String, AutonomyMode) -> Unit,
    onAppSessionApprovalOverrideToggled: (String, String) -> Unit,
    onAppSessionRevoked: (String) -> Unit,
    onTeachDraftActionTypeChanged: (String) -> Unit,
    onTeachDraftInputKeyChanged: (String) -> Unit,
    onTeachDraftTargetLabelChanged: (String) -> Unit,
    onTeachDraftFieldBindingsChanged: (String) -> Unit,
    onTeachDraftClickLabelChanged: (String) -> Unit,
    onTeachDraftClickVerifierChanged: (String) -> Unit,
    onTeachDraftRecoveryLabelsChanged: (String) -> Unit,
    onTeachDraftAccountProofChanged: (String) -> Unit,
    onTeachDraftRiskChanged: (String) -> Unit,
    onTeachDraftSourceScopeChanged: (String) -> Unit,
    onTeachAppAgent: () -> Unit
) {
    Section(title = "App autonomy") {
        InfoCard(title = "Teach observed app") {
            if (teachDraft.packageName.isBlank()) {
                Text(teachDraft.status, color = Color(0xFF64748B))
            } else {
                Text(teachDraft.packageName, fontWeight = FontWeight.SemiBold)
                Text("Observed: ${teachDraft.observedAtIso}", color = Color(0xFF64748B))
                Text(teachDraft.status, color = Color(0xFF475569))
                OutlinedTextField(
                    value = teachDraft.actionType,
                    onValueChange = onTeachDraftActionTypeChanged,
                    label = { Text("Action type") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teachDraft.inputKey,
                    onValueChange = onTeachDraftInputKeyChanged,
                    label = { Text("Input key") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = teachDraft.accountProofLabel,
                    onValueChange = onTeachDraftAccountProofChanged,
                    label = { Text("Account proof label") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.accountProofCandidates.isNotEmpty()) {
                    LabelChipRow(
                        labels = teachDraft.accountProofCandidates,
                        selected = teachDraft.accountProofLabel,
                        onSelected = onTeachDraftAccountProofChanged
                    )
                }
                Text("Risk: ${teachDraft.riskLabel}", color = Color(0xFF64748B))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("low_reversible", "medium_external_side_effect", "medium_public_side_effect").forEach { risk ->
                        FilterChip(
                            selected = teachDraft.riskLabel == risk,
                            onClick = { onTeachDraftRiskChanged(risk) },
                            label = { Text(risk) }
                        )
                    }
                }
                if (teachDraft.exactApprovalRequired) {
                    Text("Exact approval required", color = Color(0xFF9A3412))
                }
                OutlinedTextField(
                    value = teachDraft.targetLabel,
                    onValueChange = onTeachDraftTargetLabelChanged,
                    label = { Text("Target and verifier label") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.availableLabels.isNotEmpty()) {
                    LabelChipRow(
                        labels = teachDraft.availableLabels,
                        selected = teachDraft.targetLabel,
                        onSelected = onTeachDraftTargetLabelChanged
                    )
                }
                OutlinedTextField(
                    value = teachDraft.fieldBindingsText,
                    onValueChange = onTeachDraftFieldBindingsChanged,
                    label = { Text("Extra fields") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.fieldBindingSummaries.isNotEmpty()) {
                    Text("Fields: ${teachDraft.fieldBindingSummaries.joinToString()}", color = Color(0xFF64748B))
                }
                if (teachDraft.unknownFieldBindingLabels.isNotEmpty()) {
                    Text("Unknown fields: ${teachDraft.unknownFieldBindingLabels.joinToString()}", color = Color(0xFF9A3412))
                }
                OutlinedTextField(
                    value = teachDraft.clickLabel,
                    onValueChange = onTeachDraftClickLabelChanged,
                    label = { Text("Click target label") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.availableLabels.isNotEmpty()) {
                    LabelChipRow(
                        labels = teachDraft.availableLabels,
                        selected = teachDraft.clickLabel,
                        onSelected = onTeachDraftClickLabelChanged
                    )
                }
                OutlinedTextField(
                    value = teachDraft.clickVerifierLabel,
                    onValueChange = onTeachDraftClickVerifierChanged,
                    label = { Text("Click verifier label") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.availableLabels.isNotEmpty()) {
                    LabelChipRow(
                        labels = teachDraft.availableLabels,
                        selected = teachDraft.clickVerifierLabel,
                        onSelected = onTeachDraftClickVerifierChanged
                    )
                }
                OutlinedTextField(
                    value = teachDraft.recoveryLabelsText,
                    onValueChange = onTeachDraftRecoveryLabelsChanged,
                    label = { Text("Recovery labels") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.availableLabels.isNotEmpty()) {
                    LabelChipRow(
                        labels = teachDraft.availableLabels,
                        selected = "",
                        onSelected = {
                            onTeachDraftRecoveryLabelsChanged(
                                teachDraft.recoveryLabelsText.toggleSourceId(it)
                            )
                        }
                    )
                }
                if (teachDraft.selectedRecoveryLabels.isNotEmpty()) {
                    Text("Recovery: ${teachDraft.selectedRecoveryLabels.joinToString()}", color = Color(0xFF64748B))
                }
                OutlinedTextField(
                    value = teachDraft.sourceScopeText,
                    onValueChange = onTeachDraftSourceScopeChanged,
                    label = { Text("Required sources") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (teachDraft.availableSourceIds.isNotEmpty()) {
                    Text("Available sources: ${teachDraft.availableSourceIds.joinToString()}", color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        teachDraft.availableSourceIds.take(4).forEach { sourceId ->
                            FilterChip(
                                selected = teachDraft.selectedSourceIds.contains(sourceId),
                                onClick = {
                                    onTeachDraftSourceScopeChanged(
                                        teachDraft.sourceScopeText.toggleSourceId(sourceId)
                                    )
                                },
                                label = { Text(sourceId) }
                            )
                        }
                    }
                }
                if (teachDraft.unknownSourceIds.isNotEmpty()) {
                    Text("Unknown sources: ${teachDraft.unknownSourceIds.joinToString()}", color = Color(0xFF9A3412))
                }
                if (teachDraft.stepPreview.isNotEmpty()) {
                    Text("Draft steps: ${teachDraft.stepPreview.joinToString(" | ")}", color = Color(0xFF64748B))
                }
                if (teachDraft.availableLabels.isNotEmpty()) {
                    Text("Observed labels: ${teachDraft.availableLabels.take(6).joinToString()}", color = Color(0xFF64748B))
                }
                OutlinedButton(
                    onClick = onTeachAppAgent,
                    enabled = teachDraft.canSubmit
                ) {
                    Text("Dry-run and save playbook")
                }
            }
        }
        if (sessions.isEmpty()) {
            InfoCard(title = "No app grants") {
                Text("Grant an app handoff to configure per-app autonomy.")
            }
        }
        sessions.forEach { session ->
            InfoCard(title = session.packageName) {
                Text(session.loginState, fontWeight = FontWeight.SemiBold)
                StatusText(if (session.revoked) "Revoked" else "Active")
                Text(
                    "Login proof: ${session.loginProofMethod} · ${session.loginProofSubject}",
                    color = Color(0xFF475569)
                )
                Text("Verified: ${session.loginProofVerifiedAtIso}", color = Color(0xFF64748B))
                Text("Expires: ${session.expiresAtIso}", color = Color(0xFF64748B))
                Text("Autonomous actions left: ${session.remainingAutonomousActions}", color = Color(0xFF64748B))
                Text(session.allowedPlaybookIds.joinToString(), color = Color(0xFF64748B))
                Text(session.allowedSourceIds.joinToString(), color = Color(0xFF64748B))
                if (session.supportedActionTypes.isNotEmpty()) {
                    Text("Require approval", color = Color(0xFF64748B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        session.supportedActionTypes.take(4).forEach { actionType ->
                            FilterChip(
                                selected = session.approvalRequiredActionTypes.contains(actionType),
                                onClick = { onAppSessionApprovalOverrideToggled(session.packageName, actionType) },
                                label = { Text(actionType) }
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { onAppSessionRevoked(session.packageName) }) {
                    Text("Revoke agent")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AutonomyMode.values().forEach { mode ->
                        FilterChip(
                            selected = session.autonomyMode == mode,
                            onClick = { onAppSessionAutonomySelected(session.packageName, mode) },
                            label = { Text(mode.name.replace("_", " ")) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelChipRow(
    labels: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.take(4).forEach { label ->
            FilterChip(
                selected = selected == label,
                onClick = { onSelected(label) },
                label = { Text(label) }
            )
        }
    }
}

private fun String.toggleSourceId(sourceId: String): String {
    val selected = split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toMutableSet()
    if (selected.contains(sourceId)) {
        selected.remove(sourceId)
    } else {
        selected.add(sourceId)
    }
    return selected.sorted().joinToString(",")
}

@Composable
private fun HeroVoicePanel(state: LauncherUiState, onVoicePressed: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF223040)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(state.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(state.transcript, color = Color(0xFFD8E4F0), style = MaterialTheme.typography.bodyMedium)
                Text("Intent: ${state.intentType}", color = Color(0xFFB9C7D4), style = MaterialTheme.typography.labelMedium)
            }
            Button(
                onClick = onVoicePressed,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            ) {
                Text("Talk")
            }
        }
    }
}

@Composable
private fun AutonomyModeRow(selected: AutonomyMode, onAutonomySelected: (AutonomyMode) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AutonomyMode.values().forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onAutonomySelected(mode) },
                label = { Text(mode.name.replace("_", " ")) }
            )
        }
    }
}

@Composable
private fun RecommendationCard(state: LauncherUiState) {
    InfoCard(title = "Best option") {
        Text(state.recommendationTitle, fontWeight = FontWeight.Bold)
        Text("Fit score ${state.recommendationScore}")
        Text(state.goal, color = Color(0xFF64748B))
    }
}

@Composable
private fun ContextSection(cards: List<ContextCardUi>) {
    Section(title = "Context") {
        cards.forEach { card ->
            InfoCard(title = card.source) {
                Text(card.type, fontWeight = FontWeight.SemiBold)
                Text(card.summary)
                Text("Purpose: ${card.allowedPurpose}", color = Color(0xFF64748B))
                Text("Account: ${card.accountId}", color = Color(0xFF64748B))
                Text("Freshness: ${card.freshnessStatus}", color = Color(0xFF64748B))
                Text("Grants: ${card.baseGrantId} / ${card.appAgentGrantId}", color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun PlanSection(steps: List<PlanStepUi>) {
    Section(title = "Plan") {
        steps.forEach { step ->
            InfoCard(title = step.title) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(step.actionType)
                    StatusPill(step.status)
                }
                Text("Tool: ${step.tool} · Risk: ${step.risk}", color = Color(0xFF64748B))
                Text(
                    "Side effect: ${if (step.externalSideEffect) "external" else "none"}",
                    color = if (step.externalSideEffect) Color(0xFF9A3412) else Color(0xFF64748B)
                )
                if (step.requiredSourceIds.isNotEmpty()) {
                    Text("Sources: ${step.requiredSourceIds.joinToString()}", color = Color(0xFF64748B))
                }
                if (step.inputSummary.isNotEmpty()) {
                    Text("Inputs: ${step.inputSummary.joinToString()}", color = Color(0xFF64748B))
                }
                if (step.policyReason.isNotBlank()) {
                    Text("Policy: ${step.policyReason}", color = Color(0xFF475569))
                }
            }
        }
    }
}

@Composable
private fun ApprovalSection(
    approvals: List<ApprovalUi>,
    onApprovalApproved: (String) -> Unit,
    onApprovalDenied: (String) -> Unit
) {
    Section(title = "Approvals") {
        if (approvals.isEmpty()) {
            InfoCard(title = "Queue clear") {
                Text("Sensitive app actions will pause here.")
            }
        }
        approvals.forEach { approval ->
            InfoCard(title = approval.actionType) {
                Text(approval.exactContent)
                Text(approval.reason, color = Color(0xFF9A5B00))
                if (approval.stepSummaries.isNotEmpty()) {
                    Text("Steps: ${approval.stepSummaries.joinToString(" | ")}", color = Color(0xFF475569))
                }
                when (approval.status) {
                    ApprovalStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprovalApproved(approval.id) }) {
                            Text("Approve")
                        }
                        OutlinedButton(onClick = { onApprovalDenied(approval.id) }) {
                            Text("Deny")
                        }
                    }
                    ApprovalStatus.APPROVED -> StatusText("Approved and executed")
                    ApprovalStatus.DENIED -> StatusText("Denied")
                }
            }
        }
    }
}

@Composable
private fun AppHandoffSection(
    handoffs: List<AppHandoffUi>,
    onAppHandoffGranted: (String) -> Unit,
    onAppHandoffCancelled: (String) -> Unit
) {
    Section(title = "App handoffs") {
        if (handoffs.isEmpty()) {
            InfoCard(title = "Apps ready") {
                Text("Login, grants, and app-operation inputs are clear.")
            }
        }
        handoffs.forEach { handoff ->
            InfoCard(title = handoff.packageName) {
                Text(handoff.playbookId, fontWeight = FontWeight.SemiBold)
                Text(handoff.reason, color = Color(0xFF9A5B00))
                if (handoff.requiredSourceIds.isNotEmpty()) {
                    Text("Sources: ${handoff.requiredSourceIds.joinToString()}", color = Color(0xFF475569))
                }
                if (handoff.blockedBaseSourceIds.isNotEmpty()) {
                    Text("Restore base data access first: ${handoff.blockedBaseSourceIds.joinToString()}", color = Color(0xFF9A3412))
                }
                if (handoff.inputSummary.isNotEmpty()) {
                    Text("Inputs: ${handoff.inputSummary.joinToString()}", color = Color(0xFF475569))
                }
                if (handoff.missingInputKeys.isNotEmpty()) {
                    Text("Missing: ${handoff.missingInputKeys.joinToString()}", color = Color(0xFF9A3412))
                }
                if (handoff.exactContentPreview.isNotBlank()) {
                    Text("Exact content: ${handoff.exactContentPreview}", color = Color(0xFF334155))
                }
                if (handoff.stepSummaries.isNotEmpty()) {
                    Text("Steps: ${handoff.stepSummaries.joinToString(" | ")}", color = Color(0xFF475569))
                }
                if (handoff.autonomyContext.isNotBlank()) {
                    Text("Autonomy: ${handoff.autonomyContext}", color = Color(0xFF475569))
                }
                Text("Created: ${handoff.createdAtIso}", color = Color(0xFF64748B))
                Text("Expires: ${handoff.expiresAtIso}", color = Color(0xFF9A3412))
                Text(handoff.requiredUserAction)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onAppHandoffGranted(handoff.requestId) }) {
                        Text(handoff.primaryActionLabel)
                    }
                    OutlinedButton(onClick = { onAppHandoffCancelled(handoff.requestId) }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationTimelineSection(timelines: List<OperationTimelineUi>) {
    Section(title = "Operation timeline") {
        if (timelines.isEmpty()) {
            InfoCard(title = "No operation history") {
                Text("Cross-app work will appear here as Conductor plans, pauses, and verifies app actions.")
            }
        }
        timelines.forEach { timeline ->
            InfoCard(title = timeline.taskId) {
                Text("${timeline.intentType} · ${timeline.autonomyMode}", color = Color(0xFF475569))
                Text("Updated: ${timeline.updatedAtIso}", color = Color(0xFF64748B))
                timeline.events.forEach { event ->
                    Text("${event.status}: ${event.actionType}", fontWeight = FontWeight.SemiBold)
                    Text("Tool: ${event.tool}", color = Color(0xFF64748B))
                    if (event.policyDecision.isNotBlank()) {
                        Text("Policy: ${event.policyDecision}", color = Color(0xFF64748B))
                    }
                    if (event.packageName.isNotBlank()) {
                        Text("App: ${event.packageName}", color = Color(0xFF64748B))
                    }
                    if (event.playbookId.isNotBlank()) {
                        Text("Playbook: ${event.playbookId}", color = Color(0xFF64748B))
                    }
                    if (event.sourceScope.isNotEmpty()) {
                        Text("Sources: ${event.sourceScope.joinToString()}", color = Color(0xFF64748B))
                    }
                    if (event.approvalId.isNotBlank()) {
                        Text("Approval: ${event.approvalId}", color = Color(0xFF64748B))
                    }
                    Text(event.summary, color = Color(0xFF334155))
                }
            }
        }
    }
}

@Composable
private fun AppReceiptSection(receipts: List<AppReceiptUi>) {
    Section(title = "App receipts") {
        if (receipts.isEmpty()) {
            InfoCard(title = "No app receipts") {
                Text("Verified app operations will appear here.")
            }
        }
        receipts.forEach { receipt ->
            InfoCard(title = receipt.requestId) {
                StatusText(
                    when {
                        receipt.verified -> "Verified"
                        receipt.eventType == "operator.execution_preview" -> "Preview"
                        else -> "Recorded"
                    }
                )
                Text(receipt.eventType, color = Color(0xFF475569))
                if (receipt.packageName.isNotBlank()) {
                    Text("App: ${receipt.packageName}", color = Color(0xFF64748B))
                }
                if (receipt.playbookId.isNotBlank()) {
                    Text("Playbook: ${receipt.playbookId}", color = Color(0xFF64748B))
                }
                if (receipt.actionType.isNotBlank()) {
                    Text("Action: ${receipt.actionType}", color = Color(0xFF64748B))
                }
                if (receipt.accountProof.isNotBlank()) {
                    Text("Account proof: ${receipt.accountProof}", color = Color(0xFF64748B))
                }
                if (receipt.sourceScope.isNotEmpty()) {
                    Text("Sources: ${receipt.sourceScope.joinToString()}", color = Color(0xFF64748B))
                }
                if (receipt.inputSummary.isNotEmpty()) {
                    Text("Inputs: ${receipt.inputSummary.joinToString()}", color = Color(0xFF64748B))
                }
                if (receipt.stepSummaries.isNotEmpty()) {
                    Text("Steps: ${receipt.stepSummaries.joinToString(" -> ")}", color = Color(0xFF64748B))
                }
                if (receipt.exactApproval.isNotBlank()) {
                    Text("Exact approval: ${receipt.exactApproval}", color = Color(0xFF64748B))
                }
                if (receipt.autonomy.isNotBlank()) {
                    Text("Autonomy: ${receipt.autonomy}", color = Color(0xFF64748B))
                }
                Text(receipt.detail, color = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        color = Color(0xFF475569),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun AuditSection(events: List<String>) {
    Section(title = "Audit") {
        events.take(8).forEach { event ->
            InfoCard(title = "Event") {
                Text(event)
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun StatusPill(status: StepStatus) {
    val color = when (status) {
        StepStatus.SUCCEEDED -> Color(0xFFE2F4EB)
        StepStatus.AWAITING_APPROVAL -> Color(0xFFFFF1D8)
        StepStatus.BLOCKED -> Color(0xFFFFE5EA)
        StepStatus.FAILED -> Color(0xFFE7EFF5)
    }
    Text(
        text = status.name.lowercase(),
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall
    )
}

@Preview(showBackground = true)
@Composable
private fun ConductorLauncherScreenPreview() {
    ConductorLauncherScreen(
        state = LauncherUiState(
            title = "Conductor OS",
            autonomyMode = AutonomyMode.DRAFT_ONLY,
            intentType = "outdoor_activity",
            goal = "Find something outdoors and invite Maya.",
            transcript = "I found an outdoor event and paused sending for approval.",
            recommendationTitle = "Outdoor Jazz At The Garden",
            recommendationScore = 94,
            appSessions = listOf(
                AppSessionUi(
                    userId = "user_001",
                    packageName = "com.google.android.apps.messaging",
                    loginState = "LOGGED_IN",
                    loginProofMethod = "preview_account_chip",
                    loginProofSubject = "Messages signed in",
                    loginProofVerifiedAtIso = "2026-07-27T10:45:00-05:00",
                    autonomyMode = AutonomyMode.DRAFT_ONLY,
                    allowedPlaybookIds = listOf("messages_send_exact_text"),
                    allowedSourceIds = listOf("device_contacts"),
                    supportedActionTypes = listOf("outbound_message.send"),
                    approvalRequiredActionTypes = listOf("outbound_message.send"),
                    remainingAutonomousActions = 3,
                    revoked = false,
                    expiresAtIso = "2026-07-27T18:00:00-05:00"
                )
            ),
            appDiscoveries = listOf(
                AppDiscoveryUi(
                    packageName = "com.example.tasks",
                    observedAtIso = "2026-07-27T10:45:00-05:00",
                    visibleLabels = listOf("Tasks signed in (1)", "Task title (1)", "Save (1)"),
                    accountProofCandidates = listOf("Tasks signed in"),
                    bounded = false
                )
            ),
            appTeachDraft = AppTeachDraftUi(
                packageName = "com.example.tasks",
                observedAtIso = "2026-07-27T10:45:00-05:00",
                actionType = "tasks.add",
                inputKey = "title",
                targetLabel = "Task title",
                fieldBindingsText = "Task notes=body",
                fieldBindingSummaries = listOf("Task notes=body"),
                unknownFieldBindingLabels = emptyList(),
                clickLabel = "Save",
                clickVerifierLabel = "Save",
                recoveryLabelsText = "New task",
                selectedRecoveryLabels = listOf("New task"),
                accountProofLabel = "Tasks signed in",
                riskLabel = "low_reversible",
                exactApprovalRequired = false,
                sourceScopeText = "google_calendar",
                availableSourceIds = listOf("device_contacts", "google_calendar"),
                unknownSourceIds = emptyList(),
                selectedSourceIds = listOf("google_calendar"),
                availableLabels = listOf("Save", "Task title", "Tasks signed in"),
                accountProofCandidates = listOf("Tasks signed in"),
                stepPreview = listOf(
                    "set_text target=Task title input=title verify=Task title recover=New task",
                    "set_text target=Task notes input=body verify=Task notes",
                    "click target=Save verify=Save"
                ),
                canSubmit = true,
                status = "Ready to dry-run against the observed app tree."
            ),
            appSkills = listOf(
                AppSkillUi(
                    packageName = "com.google.android.apps.messaging",
                    actionCount = 1,
                    enabledActionCount = 1,
                    readyActionCount = 1,
                    blockedReasons = emptyList(),
                    requiredSourceIds = listOf("device_contacts"),
                    requiredInputKeys = listOf("exactBody", "recipient"),
                    invocationPhrases = listOf("send message", "send invite"),
                    capabilities = listOf(
                        AppCapabilityUi(
                            packageName = "com.google.android.apps.messaging",
                            playbookId = "messages_send_exact_text",
                            actionType = "outbound_message.send",
                            riskLabel = "medium_external_side_effect",
                            invocationPhrases = listOf("send message", "send invite"),
                            exactApprovalRequired = true,
                            playbookGrantActive = true,
                            requiredInputKeys = listOf("exactBody", "recipient"),
                            requiredSourceIds = listOf("device_contacts"),
                            stepSummaries = listOf(
                                "click target=conversation recipient matches input.recipient verify=recipient_thread_visible",
                                "set_text target=message input field input=exactBody verify=compose_text_equals input.exactBody"
                            ),
                            status = "Ready",
                            reason = "Can route under DRAFT_ONLY."
                        )
                    )
                )
            ),
            appCapabilities = listOf(
                AppCapabilityUi(
                    packageName = "com.google.android.apps.messaging",
                    playbookId = "messages_send_exact_text",
                    actionType = "outbound_message.send",
                    riskLabel = "medium_external_side_effect",
                    invocationPhrases = listOf("send message", "send invite"),
                    exactApprovalRequired = true,
                    playbookGrantActive = true,
                    requiredInputKeys = listOf("exactBody", "recipient"),
                    requiredSourceIds = listOf("device_contacts"),
                    stepSummaries = listOf(
                        "click target=conversation recipient matches input.recipient verify=recipient_thread_visible",
                        "set_text target=message input field input=exactBody verify=compose_text_equals input.exactBody"
                    ),
                    status = "Ready",
                    reason = "Can route under DRAFT_ONLY."
                )
            ),
            dataGrants = listOf(
                DataGrantUi(
                    id = "grant_facebook_events_activity_planning",
                    source = "facebook_events",
                    accountId = "personal",
                    purposes = listOf("activity_planning"),
                    revoked = false
                )
            ),
            connectorAccounts = listOf(
                ConnectorAccountUi(
                    source = "google_calendar",
                    accountId = "personal",
                    purposes = listOf("activity_planning", "scheduling"),
                    connected = true
                )
            ),
            appAgentGrants = listOf(
                AppAgentGrantUi(
                    id = "agent_grant_conductor_activity",
                    appAgentId = "conductor.voice",
                    packageName = "app.conductor.prototype",
                    purposes = listOf("activity_planning"),
                    sources = listOf("google_calendar", "weather_provider", "facebook_events"),
                    revoked = false
                )
            ),
            sourceFreshness = listOf(
                SourceFreshnessUi(
                    source = "google_calendar",
                    status = "Fresh",
                    factExpiresAtIso = "2026-07-28T10:45:00-05:00",
                    grantExpiresAtIso = "2026-07-28T10:45:00-05:00",
                    summary = "Free from 2:30 PM to 5:30 PM."
                )
            ),
            contextCards = listOf(
                ContextCardUi(
                    source = "calendar",
                    type = "calendar_availability",
                    summary = "Free from 2:30 PM to 5:30 PM.",
                    accountId = "personal",
                    allowedPurpose = "activity_planning",
                    freshnessStatus = "fresh_until=2026-07-28T10:45:00-05:00",
                    baseGrantId = "grant_google_calendar_activity_planning",
                    appAgentGrantId = "agent_grant_conductor_activity"
                ),
                ContextCardUi(
                    source = "weather",
                    type = "weather_hourly",
                    summary = "Clear after 1 PM.",
                    accountId = "device",
                    allowedPurpose = "activity_planning",
                    freshnessStatus = "fresh_until=2026-07-28T10:45:00-05:00",
                    baseGrantId = "grant_weather_provider_activity_planning",
                    appAgentGrantId = "agent_grant_conductor_activity"
                )
            ),
            planSteps = listOf(
                PlanStepUi(
                    id = "draft",
                    title = "Draft invite",
                    tool = "messages.create_draft",
                    actionType = "outbound_message.create_draft",
                    risk = "LOW",
                    externalSideEffect = false,
                    requiredSourceIds = listOf("device_contacts"),
                    inputSummary = listOf("recipient: contact_maya", "body: Want to go?"),
                    policyReason = "Draft creation has no external side effect.",
                    status = StepStatus.SUCCEEDED
                ),
                PlanStepUi(
                    id = "send",
                    title = "Send invite",
                    tool = "messages.send",
                    actionType = "outbound_message.send",
                    risk = "MEDIUM",
                    externalSideEffect = true,
                    requiredSourceIds = listOf("device_contacts"),
                    inputSummary = listOf("recipient: contact_maya"),
                    policyReason = "Sensitive external actions require exact user approval.",
                    status = StepStatus.AWAITING_APPROVAL
                )
            ),
            approvals = listOf(
                ApprovalUi(
                    "approval_send",
                    "outbound_message.send",
                    "Want to go?",
                    listOf(
                        "set_text target=message input field input=exactBody verify=compose_text_equals input.exactBody",
                        "click target=send button verify=sent_receipt_visible"
                    ),
                    "Sensitive external actions require exact approval.",
                    ApprovalStatus.PENDING
                )
            ),
            appHandoffs = listOf(
                AppHandoffUi(
                    requestId = "operation_send",
                    packageName = "com.google.android.apps.messaging",
                    playbookId = "messages_send_exact_text",
                    requiredSourceIds = listOf("device_contacts"),
                    blockedBaseSourceIds = emptyList(),
                    inputSummary = listOf("recipient: contact_maya"),
                    missingInputKeys = emptyList(),
                    exactContentPreview = "Want to go?",
                    stepSummaries = listOf(
                        "set_text target=message input field input=exactBody verify=compose_text_equals input.exactBody",
                        "click target=send button verify=sent_receipt_visible"
                    ),
                    reason = "No logged-in app session with grant.",
                    requiredUserAction = "Open Messages, sign in, and grant this playbook to Conductor.",
                    primaryActionLabel = "Confirm login and grant",
                    autonomyContext = "mode=ASK_ONLY; risk=low_reversible; exactApproval=false",
                    createdAtIso = "2026-07-27T10:45:00-05:00",
                    expiresAtIso = "2026-07-27T11:15:00-05:00"
                )
            ),
            operationTimelines = listOf(
                OperationTimelineUi(
                    taskId = "task_outdoor_activity",
                    intentType = "outdoor_activity",
                    autonomyMode = "DRAFT_ONLY",
                    updatedAtIso = "2026-07-27T10:45:00-05:00",
                    events = listOf(
                        OperationTimelineEventUi(
                            actionType = "outbound_message.create_draft",
                            tool = "messages.create_draft",
                            status = "SUCCEEDED",
                            policyDecision = "ALLOW",
                            approvalId = "",
                            packageName = "com.google.android.apps.messaging",
                            playbookId = "messages_create_draft",
                            sourceScope = listOf("device_contacts"),
                            summary = "accessibility_live_tree:messages_create_draft:post_state_receipt"
                        ),
                        OperationTimelineEventUi(
                            actionType = "outbound_message.send",
                            tool = "messages.send",
                            status = "QUEUED_HANDOFF",
                            policyDecision = "REQUIRE_APPROVAL",
                            approvalId = "approval_send",
                            packageName = "com.google.android.apps.messaging",
                            playbookId = "messages_send_exact_text",
                            sourceScope = listOf("device_contacts"),
                            summary = "Confirm exact content before sending."
                        )
                    )
                )
            ),
            appReceipts = listOf(
                AppReceiptUi(
                    eventType = "operator.live_verified",
                    requestId = "operation_send",
                    detail = "operation_send accessibility_live_tree:messages_send_exact_text:post_state_receipt",
                    verified = true,
                    packageName = "com.google.android.apps.messaging",
                    playbookId = "messages_send_exact_text",
                    actionType = "outbound_message.send",
                    accountProof = "visible_account:Maya",
                    sourceScope = listOf("device_contacts"),
                    inputSummary = listOf("recipient=contact_maya", "exactBody=Want to go?"),
                    stepSummaries = listOf(
                        "set_text target=message input field input=exactBody verify=compose_text_equals input.exactBody",
                        "click target=send button verify=sent_receipt_visible"
                    ),
                    exactApproval = "true:approval_send",
                    autonomy = "DRAFT_ONLY:DRAFT_ONLY:low_reversible:exactApproval=true"
                )
            ),
            auditEvents = listOf("approval.queued: outbound_message.send")
        ),
        onVoicePressed = {},
        onStopAutonomy = {},
        onAutonomySelected = {},
        onAppSessionAutonomySelected = { _, _ -> },
        onAppSessionApprovalOverrideToggled = { _, _ -> },
        onAppSessionRevoked = {},
        onAppPlaybookGrantToggled = { _, _, _ -> },
        onTeachDraftActionTypeChanged = {},
        onTeachDraftInputKeyChanged = {},
        onTeachDraftTargetLabelChanged = {},
        onTeachDraftFieldBindingsChanged = {},
        onTeachDraftClickLabelChanged = {},
        onTeachDraftClickVerifierChanged = {},
        onTeachDraftRecoveryLabelsChanged = {},
        onTeachDraftAccountProofChanged = {},
        onTeachDraftRiskChanged = {},
        onTeachDraftSourceScopeChanged = {},
        onTeachAppAgent = {},
        onDataGrantRevoked = {},
        onDataGrantRestored = {},
        onSourceRefresh = {},
        onAppAgentGrantRevoked = {},
        onAppAgentGrantRestored = {},
        onApprovalApproved = {},
        onApprovalDenied = {},
        onAppHandoffGranted = {},
        onAppHandoffCancelled = {}
    )
}
