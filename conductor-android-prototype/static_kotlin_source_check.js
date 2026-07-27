#!/usr/bin/env node

const fs = require("node:fs");
const path = require("node:path");

const root = __dirname;
const javaRoot = path.join(root, "app/src/main/java");
const resRoot = path.join(root, "app/src/main/res");

const checks = [];

const ktFiles = walk(javaRoot).filter((file) => file.endsWith(".kt"));
const ktByRelativePath = new Set(ktFiles.map((file) => path.relative(javaRoot, file)));

check("kotlin_sources_present", ktFiles.length > 0, `${ktFiles.length} Kotlin files`);

for (const file of ktFiles) {
  const relative = path.relative(javaRoot, file);
  const body = readAbsolute(file);
  const packageMatch = body.match(/^\s*package\s+([A-Za-z0-9_.]+)/m);
  const expectedPackage = path
    .dirname(relative)
    .split(path.sep)
    .join(".");

  check(
    `package_matches_path:${relative}`,
    Boolean(packageMatch) && packageMatch[1] === expectedPackage,
    `expected ${expectedPackage}`
  );

  checkBalanced(`balanced_braces:${relative}`, body, "{", "}");
  checkBalanced(`balanced_parens:${relative}`, body, "(", ")");
}

const manifest = read("app/src/main/AndroidManifest.xml");
check("manifest_exists", manifest.length > 0, "AndroidManifest.xml");
for (const className of manifestClasses(manifest)) {
  const relativePath = className
    .replace(/^\./, "app.conductor.")
    .replaceAll(".", path.sep) + ".kt";
  check(
    `manifest_class_exists:${className}`,
    ktByRelativePath.has(relativePath),
    relativePath
  );
}

for (const reference of xmlResourceReferences(manifest)) {
  check(
    `manifest_resource_exists:${reference}`,
    resourceExists(reference),
    reference
  );
}

const launcherScreen = read("app/src/main/java/app/conductor/ui/ConductorLauncherScreen.kt");
const launcherActivity = read("app/src/main/java/app/conductor/launcher/ConductorLauncherActivity.kt");
const launcherState = read("app/src/main/java/app/conductor/ui/LauncherUiState.kt");
const appOperationModels = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationModels.kt");
const appOperationExecutor = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationExecutor.kt");
const appOperationLiveBridge = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationLiveBridge.kt");
const accessibilityAppOperationLiveBridge = read("app/src/main/java/app/conductor/operator/accessibility/AccessibilityAppOperationLiveBridge.kt");
const appOperationApprovalReceiptLedger = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationApprovalReceiptLedger.kt");
const appForegroundLauncher = read("app/src/main/java/app/conductor/operator/accessibility/AppForegroundLauncher.kt");
const appOperationPlaybookRegistry = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationPlaybookRegistry.kt");
const appOperationSessionStore = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationSessionStore.kt");
const appOperationInputRepair = read("app/src/main/java/app/conductor/operator/accessibility/AppOperationInputRepair.kt");
const appAgentOnboarding = read("app/src/main/java/app/conductor/operator/accessibility/AppAgentOnboarding.kt");
const accessibilityService = read("app/src/main/java/app/conductor/operator/accessibility/ConductorAccessibilityService.kt");
const conductorRecordStore = read("app/src/main/java/app/conductor/storage/ConductorRecordStore.kt");
const inMemoryRecordStore = read("app/src/main/java/app/conductor/storage/InMemoryConductorRecordStore.kt");
const androidRecordStoreFactory = read("app/src/main/java/app/conductor/storage/AndroidConductorRecordStoreFactory.kt");
const androidPreferencesRecordStore = read("app/src/main/java/app/conductor/storage/AndroidPreferencesConductorRecordStore.kt");
const androidRecordStoreSchema = read("app/src/main/java/app/conductor/storage/AndroidRecordStoreSchema.kt");
const androidStoragePlan = read("app/src/main/java/app/conductor/storage/AndroidStoragePlan.kt");
const connectorRuntime = read("app/src/main/java/app/conductor/connectors/ConnectorRuntime.kt");
const mockOutdoorConnectors = read("app/src/main/java/app/conductor/connectors/MockOutdoorConnectors.kt");
const contextBroker = read("app/src/main/java/app/conductor/context/MockContextBroker.kt");
const personalGraphStore = read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt");
const userPreferenceMemory = read("app/src/main/java/app/conductor/graph/UserPreferenceMemory.kt");
const toolRegistry = read("app/src/main/java/app/conductor/tools/ToolRegistry.kt");
const outdoorPlanner = read("app/src/main/java/app/conductor/planner/OutdoorActivityPlanner.kt");
const conductorRuntime = read("app/src/main/java/app/conductor/runtime/ConductorRuntime.kt");
const domain = read("app/src/main/java/app/conductor/runtime/Domain.kt");
const voiceHandoffRunner = read("app/src/main/java/app/conductor/voice/VoiceHandoffRunner.kt");
const voiceIntentClassifier = read("app/src/main/java/app/conductor/voice/VoiceIntentClassifier.kt");
const voiceControlCommandParser = read("app/src/main/java/app/conductor/voice/VoiceControlCommandParser.kt");

check(
  "launcher_uses_encrypted_android_record_store",
  launcherActivity.includes("AndroidConductorRecordStoreFactory.create(applicationContext)") &&
    androidRecordStoreFactory.includes("EncryptedSharedPreferences") &&
    androidRecordStoreFactory.includes("MasterKey.KeyScheme.AES256_GCM"),
  "launcher uses retained encrypted OS memory"
);
check(
  "android_preferences_store_preserves_required_source_ids",
  androidPreferencesRecordStore.includes("requiredSourceIds = getJSONArray(\"requiredSourceIds\").toStringSet()") &&
    androidPreferencesRecordStore.includes(".put(\"requiredSourceIds\", requiredSourceIds.toJsonArray())"),
  "durable Android queue preserves required source ids"
);
check(
  "android_preferences_store_persists_safety_state",
  androidPreferencesRecordStore.includes("override fun saveConsumedApprovalReceipt") &&
    androidPreferencesRecordStore.includes("override fun appOperationSessions") &&
    androidPreferencesRecordStore.includes("override fun graphFacts") &&
    androidPreferencesRecordStore.includes("override fun approvalDecisions"),
  "durable Android store covers approvals, sessions, graph facts, and decisions"
);
check(
  "voice_handoff_classifies_mobile_intents",
  voiceIntentClassifier.includes("class VoiceIntentClassifier") &&
    voiceIntentClassifier.includes("intentType = \"app_task\"") &&
    voiceIntentClassifier.includes("intentType = \"outdoor_activity\"") &&
    voiceIntentClassifier.includes("intentType = \"os_control\"") &&
    voiceIntentClassifier.includes("intentType = \"general_mobile_intent\"") &&
    voiceIntentClassifier.includes("val controlKeywords = listOf(\"stop autonomy\", \"ask only\", \"draft only\", \"trusted auto\", \"require approval\", \"always ask\", \"ask before\", \"cancel\", \"never mind\", \"nevermind\")") &&
    voiceIntentClassifier.includes("val taskKeywords = listOf(\"task\", \"todo\", \"to-do\", \"remind\", \"reminder\")") &&
    voiceHandoffRunner.includes("fun startMobileIntentCapture") &&
    voiceHandoffRunner.includes("val classification = intentClassifier.classify(text)") &&
    voiceHandoffRunner.includes("intentHint = classification.intentType") &&
    voiceHandoffRunner.includes("controller.handoffIntent(\n                                    intentType = classification.intentType") &&
    launcherActivity.includes("voiceHandoffRunner.startMobileIntentCapture(") &&
    launcherActivity.includes("var mobileIntentType by remember") &&
    launcherActivity.includes("mobileIntentType = handoff.intentType") &&
    launcherActivity.includes("runtime.runMobileIntentWorkflow(") &&
    launcherActivity.includes("intentType = mobileIntentType") &&
    conductorRuntime.includes("fun runMobileIntentWorkflow(") &&
    conductorRuntime.includes("\"app_task\" -> runAppTaskWorkflow(") &&
    conductorRuntime.includes("\"outdoor_activity\" -> runOutdoorActivityWorkflow(") &&
    conductorRuntime.includes("else -> runGeneralMobileIntentWorkflow(") &&
    conductorRuntime.includes("private fun runAppTaskWorkflow(") &&
    conductorRuntime.includes("id = \"task_app_task\"") &&
    conductorRuntime.includes("actionType = \"tasks.add\"") &&
    conductorRuntime.includes("tool = \"app_agent.custom\"") &&
    conductorRuntime.includes("private fun runGeneralMobileIntentWorkflow(") &&
    conductorRuntime.includes("recordStore?.appOperationPlaybooks()") &&
    conductorRuntime.includes("it.actionType.startsWith(\"app_agent.observed.\")") &&
    conductorRuntime.includes("val storedPlaybooks = recordStore?.appOperationPlaybooks().orEmpty()") &&
    conductorRuntime.includes("val registry = AppOperationPlaybookRegistry(customPlaybooks = storedPlaybooks)") &&
    conductorRuntime.includes("id = \"operate_observed_app\"") &&
    conductorRuntime.includes("title = \"Operate taught app\"") &&
    conductorRuntime.includes("actionType = observedPlaybook.actionType") &&
    conductorRuntime.includes("input = inputForObservedPlaybook(observedPlaybook, utterance)") &&
    conductorRuntime.includes("intent.observed_app_route") &&
    conductorRuntime.includes("utterance: String,\n        forcedPlaybookId: String = \"\"") &&
    conductorRuntime.includes("private fun inputForObservedPlaybook(") &&
    conductorRuntime.includes("input[\"__requiredSourceIds\"] = playbook.requiredSourceIds.toList().sorted().joinToString(\",\")") &&
    conductorRuntime.includes("id = \"task_general_mobile_intent\"") &&
    conductorRuntime.includes("actionType = \"answer.generate\"") &&
    conductorRuntime.includes("tool = \"assistant.answer\"") &&
    toolRegistry.includes("\"assistant.answer\" -> \"local_mobile_intent_summary\"") &&
    conductorRuntime.includes("intent.routed") &&
    conductorRuntime.indexOf("private fun runGeneralMobileIntentWorkflow(") <
      conductorRuntime.indexOf("val storedPlaybooks = recordStore?.appOperationPlaybooks().orEmpty()") &&
    conductorRuntime.indexOf("val storedPlaybooks = recordStore?.appOperationPlaybooks().orEmpty()") <
      conductorRuntime.indexOf("private fun inputForObservedPlaybook("),
  "voice handoff records app-task, outdoor, and general mobile intent types before runtime planning"
);
check(
  "voice_can_configure_os_autonomy_and_approval_controls",
  voiceControlCommandParser.includes("class VoiceControlCommandParser") &&
    voiceControlCommandParser.includes("kind = \"set_global_autonomy\"") &&
    voiceControlCommandParser.includes("stopAllAutonomy = normalized.contains(\"stop autonomy\")") &&
    voiceControlCommandParser.includes("kind = \"set_action_approval_override\"") &&
    voiceControlCommandParser.includes("kind = \"cancel_pending_app_work\"") &&
    voiceControlCommandParser.includes("cancelAllPending = target == null") &&
    voiceControlCommandParser.includes("packageName = \"com.facebook.katana\"") &&
    voiceControlCommandParser.includes("actionType = \"public_post.create\"") &&
    voiceControlCommandParser.includes("packageName = \"com.google.android.apps.messaging\"") &&
    voiceControlCommandParser.includes("actionType = \"outbound_message.send\"") &&
    launcherActivity.includes("val voiceControlCommandParser = remember { VoiceControlCommandParser() }") &&
    launcherActivity.includes("val voiceControlCommand = voiceControlCommandParser.parse(handoff.utterance)") &&
    launcherActivity.includes("voice.control_global_autonomy_applied") &&
    launcherActivity.includes("voice.control_action_approval_applied") &&
    launcherActivity.includes("voice.control_action_approval_blocked") &&
    launcherActivity.includes("voice.control_pending_cancelled") &&
    launcherActivity.includes("voice.control_pending_cancel_blocked") &&
    launcherActivity.includes("recordStore.resolveQueuedAppOperation(queued.request.id)") &&
    launcherActivity.includes("return@startMobileIntentCapture"),
  "voice commands can change global autonomy, per-action approval requirements, and pending app work without routing through app-operation planning"
);
check(
  "tasks_persist_classified_intent_type",
  read("app/src/main/java/app/conductor/runtime/Domain.kt").includes("val intentType: String = \"outdoor_activity\"") &&
    conductorRuntime.includes("intentType = \"outdoor_activity\"") &&
    conductorRuntime.includes("intentType = \"app_task\"") &&
    conductorRuntime.includes("intentType = intentType") &&
    androidPreferencesRecordStore.includes(".put(\"intentType\", intentType)") &&
    androidPreferencesRecordStore.includes("intentType = optString(\"intentType\", \"outdoor_activity\")") &&
    tableBlock(androidRecordStoreSchema, "tasks").includes("intent_type TEXT NOT NULL DEFAULT 'outdoor_activity'") &&
    launcherState.includes("intentType = task.intentType") &&
    launcherScreen.includes("Intent: ${state.intentType}"),
  "classified mobile intent type is persisted with tasks and visible in the launcher"
);
check(
  "approval_decisions_bind_exact_content",
  conductorRecordStore.includes("val actionType: String? = null") &&
    conductorRecordStore.includes("val exactContent: String? = null") &&
    androidPreferencesRecordStore.includes(".put(\"actionType\", actionType)") &&
    androidPreferencesRecordStore.includes(".put(\"exactContent\", exactContent)") &&
    tableBlock(androidRecordStoreSchema, "approval_decisions").includes("action_type TEXT") &&
    tableBlock(androidRecordStoreSchema, "approval_decisions").includes("exact_content TEXT"),
  "stored approval decisions retain action type and exact content"
);
check(
  "runtime_rejects_id_only_or_mismatched_approvals",
  launcherActivity.includes("approvalDecisionStore.approve(approval)") &&
    launcherActivity.includes("approvedApprovalDecisions = approvalDecisionStore.approvedDecisions()") &&
    read("app/src/main/java/app/conductor/runtime/ConductorRuntime.kt").includes("it.actionType == approval.actionType") &&
    read("app/src/main/java/app/conductor/runtime/ConductorRuntime.kt").includes("it.exactContent == approval.exactContent") &&
    read("app/src/main/java/app/conductor/runtime/ConductorRuntime.kt").includes("approval.rejected"),
  "runtime approval retry requires the stored decision to match current exact content"
);
check(
  "approval_cards_surface_app_operation_steps",
  launcherState.includes("data class ApprovalUi") &&
    launcherState.includes("val stepSummaries: List<String>") &&
    launcherState.includes("firstOrNull { it.actionType == approval.actionType }") &&
    launcherState.includes("?.map { it.toStepSummary() }") &&
    launcherScreen.includes("Steps: ${approval.stepSummaries.joinToString(\" | \")}") &&
    launcherScreen.includes("ApprovalSection("),
  "approval cards show the live app-agent step operations unlocked by exact approval"
);
check(
  "launcher_plan_steps_are_action_reviews",
  launcherState.includes("data class PlanStepUi") &&
    launcherState.includes("val tool: String") &&
    launcherState.includes("val risk: String") &&
    launcherState.includes("val externalSideEffect: Boolean") &&
    launcherState.includes("val requiredSourceIds: List<String>") &&
    launcherState.includes("val inputSummary: List<String>") &&
    launcherState.includes("val policyReason: String") &&
    launcherState.includes("requiredSourceIds = input[\"__requiredSourceIds\"]") &&
    launcherState.includes("inputSummary = input.toInputSummary()") &&
    launcherState.includes("policyReason = result?.policy?.reason.orEmpty()") &&
    launcherScreen.includes("Tool: ${step.tool} · Risk: ${step.risk}") &&
    launcherScreen.includes("Side effect: ${if (step.externalSideEffect) \"external\" else \"none\"}") &&
    launcherScreen.includes("Sources: ${step.requiredSourceIds.joinToString()}") &&
    launcherScreen.includes("Inputs: ${step.inputSummary.joinToString()}") &&
    launcherScreen.includes("Policy: ${step.policyReason}"),
  "launcher plan rows show tool, risk, side effect, data scope, concrete inputs, policy reason, and status before app operation"
);
check(
  "durable_operation_timelines_surface_cross_app_work",
  read("app/src/main/java/app/conductor/runtime/Domain.kt").includes("data class OperationTimeline(") &&
    read("app/src/main/java/app/conductor/runtime/Domain.kt").includes("data class OperationTimelineEvent(") &&
    conductorRecordStore.includes("fun saveOperationTimeline(timeline: OperationTimeline)") &&
    conductorRecordStore.includes("fun operationTimelines(): List<OperationTimeline>") &&
    inMemoryRecordStore.includes("private val operationTimelineRecords = linkedMapOf<String, OperationTimeline>()") &&
    androidPreferencesRecordStore.includes("override fun saveOperationTimeline") &&
    androidPreferencesRecordStore.includes("KEY_OPERATION_TIMELINES") &&
    tableBlock(androidRecordStoreSchema, "operation_timelines").includes("events_json TEXT NOT NULL") &&
    androidStoragePlan.includes("\"operation_timelines\"") &&
    conductorRuntime.includes("private fun persistOperationTimeline(") &&
    conductorRuntime.includes("store.saveOperationTimeline(") &&
    conductorRuntime.includes("OperationTimelineEvent(") &&
    conductorRuntime.includes("status = queuedForStep?.let { \"QUEUED_HANDOFF\" } ?: (result?.status?.name ?: \"PLANNED\")") &&
    conductorRuntime.includes("auditLedger.record(\"operation.timeline_saved\"") &&
    launcherActivity.includes("operationTimelines = recordStore.operationTimelines()") &&
    launcherState.includes("data class OperationTimelineUi") &&
    launcherState.includes("data class OperationTimelineEventUi") &&
    launcherState.includes("operationTimelines = operationTimelines.toOperationTimelineUi(task.id)") &&
    launcherScreen.includes("OperationTimelineSection(state.operationTimelines)") &&
    launcherScreen.includes("Section(title = \"Operation timeline\")") &&
    launcherScreen.includes("Policy: ${event.policyDecision}") &&
    launcherScreen.includes("Sources: ${event.sourceScope.joinToString()}"),
  "task-level timelines persist and surface plan, policy, approval, handoff, app, and source-scope state across app work"
);
check(
  "connector_runtime_restores_persisted_accounts",
  connectorRuntime.includes("connectorAccounts()") &&
    connectorRuntime.includes("connector.accounts_restored") &&
    connectorRuntime.includes("fun connectedAccounts()"),
  "connected app accounts survive launcher/runtime restart"
);
check(
  "context_broker_reuses_retained_graph_before_connectors",
  contextBroker.indexOf("val retainedContext = graph.toContextBundleForAppAgent") <
    contextBroker.includes("defaultOutdoorConnectorRuntime(auditLedger, recordStore, androidContext).hydrateGraph") &&
    contextBroker.includes("context.restored_from_graph") &&
    contextBroker.includes("context.cache_miss") &&
    contextBroker.includes("items.keys.containsAll(requiredOutdoorContextKeys)"),
  "fresh retained graph context is used before connector hydration"
);
check(
  "context_items_carry_auditable_source_provenance",
  domain.includes("val accountId: String = \"\"") &&
    domain.includes("val factId: String = \"\"") &&
    domain.includes("val allowedPurpose: String = \"\"") &&
    domain.includes("val freshnessStatus: String = \"\"") &&
    domain.includes("val baseGrantId: String = \"\"") &&
    domain.includes("val appAgentGrantId: String = \"\"") &&
    personalGraphStore.includes("val grant = matchingGrant(fact, purpose)") &&
    personalGraphStore.includes("factId = fact.id") &&
    personalGraphStore.includes("allowedPurpose = purpose") &&
    personalGraphStore.includes("freshnessStatus = fact.expiresAtIso?.let { \"fresh_until=$it\" } ?: \"fresh_without_expiry\"") &&
    personalGraphStore.includes("baseGrantId = grant.id") &&
    personalGraphStore.includes("val appAgentGrant = matchingAppAgentGrant(appAgentId, purpose, sources)") &&
    personalGraphStore.includes("appAgentGrantId = appAgentGrant.id") &&
    launcherState.includes("val allowedPurpose: String") &&
    launcherState.includes("val freshnessStatus: String") &&
    launcherState.includes("appAgentGrantId = appAgentGrantId") &&
    launcherScreen.includes("Purpose: ${card.allowedPurpose}") &&
    launcherScreen.includes("Freshness: ${card.freshnessStatus}") &&
    launcherScreen.includes("Grants: ${card.baseGrantId} / ${card.appAgentGrantId}"),
  "cross-app context cards preserve source fact, account, grant, purpose, and freshness provenance"
);
check(
  "voice_activity_preferences_are_purpose_scoped_graph_memory",
  userPreferenceMemory.includes("class UserPreferenceMemory") &&
    userPreferenceMemory.includes("fun captureActivityPreference") &&
    userPreferenceMemory.includes("source = SOURCE") &&
    userPreferenceMemory.includes("allowedPurposes = setOf(PURPOSE)") &&
    userPreferenceMemory.includes("memory.preference_saved") &&
    personalGraphStore.includes("\"conductor_memory\" -> \"preferences\"") &&
    contextBroker.includes("UserPreferenceMemory(auditLedger).captureActivityPreference(task.goal, graph)") &&
    contextBroker.includes("\"conductor_memory\"") &&
    contextBroker.includes("val requiredOutdoorContextKeys = setOf(\"calendar\", \"weather\", \"events\", \"contacts\", \"maps\")") &&
    outdoorPlanner.includes("val preferenceSummary = context.items[\"preferences\"]?.summary.orEmpty()") &&
    outdoorPlanner.includes("val eventsSummary = context.items[\"events\"]?.summary.orEmpty()") &&
    outdoorPlanner.includes("val weatherSummary = context.items[\"weather\"]?.summary.orEmpty()") &&
    outdoorPlanner.includes("val calendarSummary = context.items[\"calendar\"]?.summary.orEmpty()") &&
    outdoorPlanner.includes("id = \"apply_user_preferences\"") &&
    outdoorPlanner.includes("actionType = \"memory.preference.read\"") &&
    outdoorPlanner.includes("private fun recommendationFor(") &&
    outdoorPlanner.includes("preferenceSummary: String,") &&
    outdoorPlanner.includes("eventsSummary: String,") &&
    outdoorPlanner.includes("weatherSummary: String,") &&
    outdoorPlanner.includes("calendarSummary: String") &&
    toolRegistry.includes("\"memory.preference.read\" -> \"purpose_scoped_graph_memory\""),
  "voice-captured activity preferences and multi-app outdoor context influence ranking"
);
check(
  "app_session_declares_expiry_check",
  appOperationModels.includes("fun isExpired") && appOperationModels.includes("expiresAtIso <= nowIso"),
  "AppOperationSession.isExpired"
);
check(
  "app_session_requires_login_proof",
  appOperationModels.includes("data class AppLoginProof") &&
    appOperationModels.includes("fun hasLoginProof") &&
    appOperationModels.includes("hasLoginProof()") &&
    appOperationExecutor.includes("!session.hasLoginProof()") &&
    tableBlock(androidRecordStoreSchema, "app_operation_sessions").includes("login_proof_json TEXT NOT NULL"),
  "logged-in app-operation sessions require persisted login proof"
);
check(
  "android_store_persists_app_login_proof",
  androidPreferencesRecordStore.includes(".put(\"loginProof\", loginProof.toJson())") &&
    androidPreferencesRecordStore.includes("loginProof = getJSONObject(\"loginProof\").toAppLoginProof()") &&
    androidPreferencesRecordStore.includes("private fun AppLoginProof.toJson"),
  "Android record store serializes app login proof"
);
check(
  "handoff_and_onboarding_create_login_proof",
  launcherActivity.includes("method = \"user_confirmed_app_handoff\"") &&
    appAgentOnboarding.includes("method = \"observed_accessibility_tree_dry_run\""),
  "new app-agent sessions include login proof"
);
check(
  "launcher_surfaces_app_login_proof",
  launcherState.includes("loginProofMethod") &&
    launcherState.includes("loginProofSubject") &&
    launcherState.includes("loginProofVerifiedAtIso") &&
    launcherScreen.includes("Login proof:") &&
    launcherScreen.includes("Verified:"),
  "launcher shows why an app is considered logged in"
);
check(
  "app_session_autonomy_budget_is_enforced_and_visible",
  appOperationModels.includes("val remainingAutonomousActions: Int = 3") &&
    tableBlock(androidRecordStoreSchema, "app_operation_sessions").includes("remaining_autonomous_actions INTEGER NOT NULL DEFAULT 3") &&
    androidPreferencesRecordStore.includes(".put(\"remainingAutonomousActions\", remainingAutonomousActions)") &&
    androidPreferencesRecordStore.includes("remainingAutonomousActions = optInt(\"remainingAutonomousActions\", 3)") &&
    launcherState.includes("val remainingAutonomousActions: Int") &&
    launcherState.includes("remainingAutonomousActions = remainingAutonomousActions") &&
    launcherScreen.includes("Autonomous actions left: ${session.remainingAutonomousActions}") &&
    appOperationExecutor.includes("session.remainingAutonomousActions <= 0") &&
    appOperationExecutor.includes("primaryActionLabel = \"Renew autonomy budget\"") &&
    appOperationExecutor.includes("remainingAutonomousActions = (session.remainingAutonomousActions - 1).coerceAtLeast(0)") &&
    appOperationExecutor.includes("sessionStore.saveSession(updatedSession)") &&
    appOperationExecutor.includes("\"operator.autonomy_budget_consumed\"") &&
    appOperationExecutor.includes("budget=${session.remainingAutonomousActions}") &&
    launcherActivity.includes("remainingAutonomousActions = if (mode == AutonomyMode.ASK_ONLY) 0 else 3") &&
    launcherActivity.includes("queued.primaryActionLabel == \"Renew autonomy budget\"") &&
    launcherActivity.includes("existingSession.copy(remainingAutonomousActions = 3)") &&
    launcherActivity.includes("\"operator.autonomy_budget_renewed\"") &&
    launcherActivity.includes("\"operator.autonomy_budget_renewal_blocked\""),
  "per-app autonomy has a persisted visible action budget that pauses and renews without broadening grants"
);
check(
  "app_handoffs_surface_autonomy_context",
  appOperationModels.includes("val autonomyContext: String = \"\"") &&
    appOperationExecutor.includes("operator.autonomy_handoff") &&
    appOperationExecutor.includes("auditDetail") &&
    tableBlock(androidRecordStoreSchema, "app_operation_queue").includes("autonomy_context TEXT NOT NULL") &&
    androidPreferencesRecordStore.includes(".put(\"autonomyContext\", autonomyContext)") &&
    launcherState.includes("autonomyContext = autonomyContext") &&
    launcherScreen.includes("Autonomy:"),
  "queued app handoffs explain per-app autonomy decisions"
);
check(
  "queued_app_handoffs_expire_before_retry",
  appOperationModels.includes("val createdAtIso: String = \"2026-07-27T10:45:00-05:00\"") &&
    appOperationModels.includes("val expiresAtIso: String = \"2026-07-27T11:15:00-05:00\"") &&
    appOperationModels.includes("fun isExpired(nowIso: String): Boolean = expiresAtIso <= nowIso") &&
    appOperationExecutor.includes("val createdAtIso = nowIso()") &&
    appOperationExecutor.includes("expiresAtIso = handoffExpiresAtIso(createdAtIso)") &&
    appOperationExecutor.includes("private fun handoffExpiresAtIso(createdAtIso: String): String") &&
    read("app/src/main/java/app/conductor/operator/accessibility/AppOperationQueue.kt").includes("items.removeAll { it.isExpired(nowIso()) }") &&
    read("app/src/main/java/app/conductor/operator/accessibility/AppOperationQueue.kt").includes("recordStore.resolveQueuedAppOperation(queued.request.id)") &&
    androidPreferencesRecordStore.includes(".put(\"createdAtIso\", createdAtIso)") &&
    androidPreferencesRecordStore.includes(".put(\"expiresAtIso\", expiresAtIso)") &&
    androidPreferencesRecordStore.includes("createdAtIso = optString(\"createdAtIso\", \"2026-07-27T10:45:00-05:00\")") &&
    androidPreferencesRecordStore.includes("expiresAtIso = optString(\"expiresAtIso\", \"2026-07-27T11:15:00-05:00\")") &&
    tableBlock(androidRecordStoreSchema, "app_operation_queue").includes("created_at_iso TEXT NOT NULL DEFAULT '2026-07-27T10:45:00-05:00'") &&
    tableBlock(androidRecordStoreSchema, "app_operation_queue").includes("expires_at_iso TEXT NOT NULL DEFAULT '2026-07-27T11:15:00-05:00'") &&
    launcherState.includes("expiresAtIso = expiresAtIso") &&
    launcherActivity.includes("operator.queue_expired") &&
    launcherActivity.includes("operator.handoff_expired") &&
    accessibilityService.includes("accessibility.queue_expired") &&
    launcherScreen.includes("Expires: ${handoff.expiresAtIso}"),
  "queued app handoffs carry durable expiry and are pruned before display, UI retry, or accessibility dispatch"
);
check(
  "app_handoffs_use_specific_action_labels",
  appOperationModels.includes("val primaryActionLabel: String = \"Grant and retry\"") &&
    appOperationExecutor.includes("primaryActionLabel = \"Apply autonomy and retry\"") &&
    appOperationExecutor.includes("primaryActionLabel = \"Review inputs\"") &&
    tableBlock(androidRecordStoreSchema, "app_operation_queue").includes("primary_action_label TEXT NOT NULL") &&
    androidPreferencesRecordStore.includes(".put(\"primaryActionLabel\", primaryActionLabel)") &&
    launcherState.includes("primaryActionLabel = primaryActionLabel") &&
    launcherScreen.includes("handoff.primaryActionLabel") &&
    launcherScreen.includes("onAppHandoffCancelled(handoff.requestId)") &&
    launcherActivity.includes("operator.handoff_cancelled_by_user") &&
    launcherActivity.includes("operator.handoff_cancel_blocked"),
  "queued app handoff buttons match the required user action"
);
check(
  "app_handoffs_surface_scope_and_inputs",
  launcherState.includes("val requiredSourceIds: List<String>") &&
    launcherState.includes("val blockedBaseSourceIds: List<String>") &&
    launcherState.includes("val inputSummary: List<String>") &&
    launcherState.includes("val missingInputKeys: List<String>") &&
    launcherState.includes("val exactContentPreview: String") &&
    launcherState.includes("val stepSummaries: List<String>") &&
    launcherState.includes("requiredSourceIds = request.requiredSourceIds.toList().sorted()") &&
    launcherState.includes("blockedBaseSourceIds = request.requiredSourceIds") &&
    launcherState.includes("missingInputKeys = reason.missingInputKeys()") &&
    launcherState.includes("exactContentPreview = request.input.exactApprovalContent()") &&
    launcherState.includes("private fun Map<String, String>.exactApprovalContent(): String") &&
    launcherState.includes("appHandoffs = queuedAppOperations.map { it.toAppHandoffUi(graphGrants, appOperationPlaybooks) }") &&
    launcherState.includes("appOperationPlaybooks: List<AppOperationPlaybook>") &&
    launcherState.includes("it.id == request.playbookId && it.packageName == request.packageName") &&
    launcherState.includes("?.map { it.toStepSummary() }") &&
    launcherScreen.includes("Sources: ${handoff.requiredSourceIds.joinToString()}") &&
    launcherScreen.includes("Restore base data access first: ${handoff.blockedBaseSourceIds.joinToString()}") &&
    launcherScreen.includes("Inputs: ${handoff.inputSummary.joinToString()}") &&
    launcherScreen.includes("Missing: ${handoff.missingInputKeys.joinToString()}") &&
    launcherScreen.includes("Exact content: ${handoff.exactContentPreview}") &&
    launcherScreen.includes("Steps: ${handoff.stepSummaries.joinToString(\" | \")}"),
  "queued app handoffs show source scope, blocked base data sources, app inputs, exact outbound content, and live step operations"
);
check(
  "app_sessions_support_per_action_approval_overrides",
  appOperationModels.includes("val approvalRequiredActionTypes: Set<String> = emptySet()") &&
    appOperationModels.includes("fun requiresApprovalFor(actionType: String): Boolean") &&
    appOperationSessionStore.includes("approvalRequiredActionTypes = setOf(\"outbound_message.send\")") &&
    appOperationSessionStore.includes("approvalRequiredActionTypes = setOf(\"public_post.create\")") &&
    androidPreferencesRecordStore.includes(".put(\"approvalRequiredActionTypes\", approvalRequiredActionTypes.toJsonArray())") &&
    androidPreferencesRecordStore.includes("approvalRequiredActionTypes = optJSONArray(\"approvalRequiredActionTypes\")?.toStringSet() ?: emptySet()") &&
    tableBlock(androidRecordStoreSchema, "app_operation_sessions").includes("approval_required_action_types_json TEXT NOT NULL DEFAULT '[]'") &&
    appOperationExecutor.includes("val exactApprovalRequired = playbook.requiresExactApproval || session.requiresApprovalFor(playbook.actionType)") &&
    appOperationExecutor.includes("val exactBody = exactApprovalContent(request)") &&
    appOperationExecutor.includes("sessionApprovalOverride=${session.requiresApprovalFor(playbook.actionType)}") &&
    launcherState.includes("val supportedActionTypes: List<String>") &&
    launcherState.includes("val approvalRequiredActionTypes: List<String>") &&
    launcherState.includes("session?.requiresApprovalFor(playbook.actionType) == true") &&
    launcherScreen.includes("Text(\"Require approval\"") &&
    launcherScreen.includes("onClick = { onAppSessionApprovalOverrideToggled(session.packageName, actionType) }") &&
    launcherActivity.includes("onAppSessionApprovalOverrideToggled = { packageName, actionType ->") &&
    launcherActivity.includes("operator.session_approval_override_updated"),
  "users can require exact approval for individual app action types without disabling the whole app session"
);
check(
  "voice_can_repair_missing_app_inputs",
  appOperationInputRepair.includes("class AppOperationInputRepair") &&
    appOperationInputRepair.includes("val missingInputKeys = playbook.requiredInputKeys") &&
    appOperationInputRepair.includes("queued.request.input[it].isNullOrBlank()") &&
    appOperationInputRepair.includes("request = queued.request.copy(input = repairedInput)") &&
    appOperationInputRepair.includes("operator.input_repair_attempted") &&
    appOperationInputRepair.includes("\"recipient\" -> afterAny") &&
    appOperationInputRepair.includes("\"destination\" -> afterAny") &&
    appOperationInputRepair.includes("\"exactBody\" -> cleaned") &&
    launcherActivity.includes("val appOperationInputRepair = remember { AppOperationInputRepair(runtimeAuditLedger) }") &&
    launcherActivity.includes(".firstOrNull { it.primaryActionLabel == \"Review inputs\" }") &&
    launcherActivity.includes("queuedReview.request.userId == activeUserId") &&
    launcherActivity.includes("appOperationInputRepair.repairFromUtterance(") &&
    launcherActivity.includes("appOperationExecutor.execute(repaired.request)") &&
    launcherActivity.includes("operator.input_repair_retry_verified") &&
    launcherActivity.includes("operator.input_repair_retry_pending") &&
    launcherActivity.includes("operator.input_repair_blocked"),
  "voice follow-up can fill declared missing app inputs and retry the original queued operation without minting grants"
);
check(
  "voice_can_revise_pending_exact_content",
  appOperationInputRepair.includes("fun reviseExactContentFromUtterance(") &&
    appOperationInputRepair.includes("approvalReceipt = null") &&
    appOperationInputRepair.includes("\"operator.exact_content_revised\"") &&
    appOperationInputRepair.includes("\"change it to \"") &&
    appOperationInputRepair.includes("\"revise it to \"") &&
    launcherActivity.includes(".firstOrNull { it.primaryActionLabel == \"Approve exact content\" }") &&
    launcherActivity.includes("handoff.utterance.looksLikeExactContentRevision()") &&
    launcherActivity.includes("appOperationInputRepair.reviseExactContentFromUtterance(") &&
    launcherActivity.includes("recordStore.enqueueAppOperation(") &&
    launcherActivity.includes("queuedExactApproval.copy(request = revised.request)") &&
    launcherActivity.includes("operator.exact_content_revision_pending_approval") &&
    launcherActivity.includes("operator.exact_content_revision_blocked") &&
    launcherActivity.includes("private fun String.looksLikeExactContentRevision(): Boolean"),
  "voice follow-up can revise queued exact outbound content and keep it pending for approval"
);
check(
  "launcher_does_not_grant_sessions_for_review_handoffs",
  launcherActivity.includes("queued.primaryActionLabel == \"Review inputs\"") &&
    launcherActivity.includes("queued.primaryActionLabel == \"Approve exact content\"") &&
    launcherActivity.includes("operator.handoff_review_required") &&
    launcherActivity.includes("existingSession?.loginProof") &&
    launcherActivity.includes("allowedPlaybookIds = session.allowedPlaybookIds + queued.request.playbookId") &&
    launcherActivity.includes("allowedSourceIds = session.allowedSourceIds + queued.request.requiredSourceIds"),
  "review and approval handoffs do not mint app-session grants"
);
check(
  "built_in_plan_steps_route_through_app_agents",
  toolRegistry.includes("agentOperatedActionTypes") &&
    toolRegistry.includes("\"outbound_message.create_draft\"") &&
    toolRegistry.includes("\"outbound_message.send\"") &&
    toolRegistry.includes("\"calendar.hold.create\"") &&
    toolRegistry.includes("\"maps.route.open\"") &&
    toolRegistry.includes("\"public_post.create\"") &&
    toolRegistry.includes("return executeAppOperation(step, approval, userId)"),
  "agent-capable plan steps go through app-operation routing instead of bare intents"
);
check(
  "draft_message_has_app_agent_playbook_and_grant",
  appOperationPlaybookRegistry.includes("id = \"messages_draft_invite\"") &&
    appOperationPlaybookRegistry.includes("actionType = \"outbound_message.create_draft\"") &&
    appOperationExecutor.includes("\"outbound_message.create_draft\" -> setOf(\"recipient\", \"body\")") &&
    read("app/src/main/java/app/conductor/operator/accessibility/AppOperationSessionStore.kt").includes("setOf(\"messages_draft_invite\", \"messages_send_exact_text\")"),
  "draft invites are app-agent playbooks scoped to the logged-in messaging app"
);
check(
  "agent_routed_steps_preserve_required_sources",
  toolRegistry.includes("\"outbound_message.create_draft\" -> setOf(\"device_contacts\")") &&
    toolRegistry.includes("\"calendar.hold.create\" -> setOf(\"google_calendar\")") &&
    toolRegistry.includes("\"maps.route.open\" -> setOf(\"maps\")") &&
    toolRegistry.includes("\"public_post.create\" -> setOf(\"facebook_events\")"),
  "app-agent operation routes carry the source grants required by the user intent"
);
check(
  "app_operations_require_live_app_agent_source_grant",
  appOperationExecutor.includes("interface AppOperationSourceAuthorizer") &&
    appOperationExecutor.includes("RecordBackedAppOperationSourceAuthorizer") &&
    appOperationExecutor.includes("recordStore.appAgentGrants()") &&
    appOperationExecutor.includes("recordStore.graphGrants()") &&
    appOperationExecutor.includes("activeBaseSources") &&
    appOperationExecutor.includes("!grant.revoked") &&
    appOperationExecutor.includes("!grant.isExpired(nowIso())") &&
    appOperationExecutor.includes("app_agent_source_grant_required") &&
    launcherActivity.includes("sourceAuthorizer = RecordBackedAppOperationSourceAuthorizer(recordStore)"),
  "app operations cannot use required source ids unless base and app-agent data grants are live"
);
check(
  "app_operations_require_signed_in_conductor_account",
  appOperationExecutor.includes("private fun hasSignedInUser") &&
    appOperationExecutor.includes("return blockedRoute(actionType, \"signed_in_account_required\")") &&
    appOperationExecutor.includes("return blocked(request, \"Signed-in Conductor account required for app operation\")") &&
    launcherActivity.includes("val activeUserId = accountSessionStore.currentSession()?.userId") &&
    launcherActivity.includes("queued.request.userId == \"signed_out\"") &&
    launcherActivity.includes("queued.request.userId != activeUserId") &&
    launcherActivity.includes("operator.handoff_account_mismatch") &&
    launcherActivity.indexOf("operator.handoff_account_mismatch") <
      launcherActivity.indexOf("method = \"user_confirmed_app_handoff\""),
  "app operations and handoff acceptance require the queued user to match the signed-in Conductor account"
);
check(
  "source_restore_handoffs_do_not_mint_sessions",
  appOperationExecutor.includes("primaryActionLabel = \"Restore data access\"") &&
    launcherActivity.includes("queued.primaryActionLabel == \"Restore data access\"") &&
    launcherActivity.includes("app_agent.grant_restored_from_handoff") &&
    launcherActivity.indexOf("queued.primaryActionLabel == \"Restore data access\"") <
      launcherActivity.indexOf("queued.primaryActionLabel == \"Approve exact content\""),
  "revoked source grants require restore handoff instead of creating app sessions"
);
check(
  "source_restore_handoffs_restore_scope_and_retry",
  launcherActivity.includes("val restoredSourceIds = queued.request.requiredSourceIds") &&
    launcherActivity.includes("val appAgentGrantId = \"agent_grant_${queued.request.packageName}_${queued.request.playbookId}\"") &&
    launcherActivity.includes("val missingBaseSourceIds") &&
    launcherActivity.includes("val restorableBaseGrants") &&
    launcherActivity.includes("val unconnectedSourceIds") &&
    launcherActivity.includes("graph.grant_connect_required") &&
    launcherActivity.includes("graph.grant_restored_from_handoff") &&
    launcherActivity.includes("graph.grant_restore_required") &&
    launcherActivity.includes("sources = existingGrant.sources + restoredSourceIds") &&
    launcherActivity.includes("it.packageName == queued.request.packageName") &&
    launcherActivity.includes("packageName = queued.request.packageName") &&
    launcherActivity.includes("${restoredGrant.packageName}:${restoredSourceIds.joinToString()}") &&
    launcherActivity.includes("val restoredGrantExpiresAtIso") &&
    launcherActivity.includes("val execution = appOperationExecutor.execute(queued.request)") &&
    launcherActivity.includes("operator.source_restore_retry_verified") &&
    launcherActivity.includes("operator.source_restore_retry_pending"),
  "restore-data-access handoffs restore only queued source scope, refresh expiry, and retry"
);
check(
  "handoff_grants_renew_session_expiry",
  launcherActivity.includes("val renewedSessionExpiresAtIso") &&
    launcherActivity.includes("expiresAtIso = renewedSessionExpiresAtIso") &&
    launcherActivity.includes("revoked = false,\n                                    expiresAtIso = renewedSessionExpiresAtIso"),
  "accepting a login/grant handoff renews the bounded app-agent session expiry"
);
check(
  "handoff_grants_retry_before_resolving_queue",
  launcherActivity.includes("val execution = appOperationExecutor.execute(queued.request)") &&
    launcherActivity.includes("execution.status == AppOperationStatus.VERIFIED") &&
    launcherActivity.includes("operator.handoff_grant_retry_verified") &&
    launcherActivity.includes("operator.handoff_grant_retry_pending") &&
    launcherActivity.indexOf("val execution = appOperationExecutor.execute(queued.request)") <
      launcherActivity.indexOf("operator.handoff_grant_retry_verified"),
  "accepting a login/grant handoff retries the queued app operation and resolves only after verification"
);
check(
  "launcher_exact_handoff_retries_with_content_bound_receipt",
  launcherActivity.includes("AppOperationApprovalReceipt") &&
    launcherActivity.includes("approvedExactContent = exactContent") &&
    launcherActivity.includes("val exactContent = queued.request.input.exactApprovalContent()") &&
    launcherActivity.includes("private fun Map<String, String>.exactApprovalContent(): String") &&
    launcherActivity.includes("actionType = playbook.actionType") &&
    launcherActivity.includes("queued.request.copy(approvalReceipt = receipt)") &&
    launcherActivity.includes("execution.status == AppOperationStatus.VERIFIED") &&
    launcherActivity.indexOf("execution.status == AppOperationStatus.VERIFIED") <
      launcherActivity.indexOf("operator.exact_handoff_approved"),
  "exact-content handoffs retry only with a matching receipt and resolve after verification"
);
check(
  "in_memory_app_operation_queue_dedupes_request_ids",
  inMemoryRecordStore.includes("appOperationQueueRecords.removeAll { it.request.id == item.request.id }") &&
    androidPreferencesRecordStore.includes("upsert(KEY_APP_OPERATION_QUEUE, item.request.id, item.toJson())"),
  "queued app-operation retries replace the same request id"
);
check(
  "app_operation_session_schema_persists_revocation",
  tableBlock(androidRecordStoreSchema, "app_operation_sessions").includes("revoked INTEGER NOT NULL DEFAULT 0") &&
    appOperationModels.includes("val revoked: Boolean = false") &&
    appOperationExecutor.includes("session?.revoked == true"),
  "app-operation session revocation survives persistence"
);
check(
  "graph_fact_schema_persists_expiry",
  tableBlock(androidRecordStoreSchema, "graph_facts").includes("expires_at_iso TEXT") &&
    androidStoragePlan.includes('"weather_hourly" to "expire_after_24_hours"'),
  "cross-app graph facts persist expiry for freshness filtering"
);
check(
  "graph_grant_schema_persists_expiry",
  tableBlock(androidRecordStoreSchema, "graph_grants").includes("expires_at_iso TEXT") &&
    tableBlock(androidRecordStoreSchema, "app_agent_grants").includes("expires_at_iso TEXT"),
  "cross-app graph and app-agent grants persist expiry"
);
check(
  "app_operation_queue_schema_persists_required_sources",
  tableBlock(androidRecordStoreSchema, "app_operation_queue").includes("required_source_ids_json TEXT NOT NULL") &&
    launcherActivity.includes("allowedSourceIds = session.allowedSourceIds + queued.request.requiredSourceIds"),
  "queued app handoffs preserve source-grant scope"
);
check(
  "personal_graph_filters_expired_facts",
  appOperationModels.length > 0 &&
    read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt").includes("val expiresAtIso: String? = null") &&
    read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt").includes("fun isExpired(nowIso: String)") &&
    read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt").includes("!fact.isExpired(nowIso())"),
  "expired cross-app facts are withheld from model context"
);
check(
  "personal_graph_filters_expired_grants",
  read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt").includes("data class GraphGrant") &&
    read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt").includes("data class AppAgentGrant") &&
    read("app/src/main/java/app/conductor/graph/PersonalGraphStore.kt").includes("!grant.isExpired(nowIso())"),
  "expired cross-app grants cannot authorize model context"
);
check(
  "app_route_blocks_expired_session",
  appOperationExecutor.includes("session.isExpired(nowIso())") &&
    appOperationExecutor.includes("agent_route.session_expired") &&
    appOperationExecutor.includes("reason = \"session_expired\""),
  "expired sessions require renewal handoff"
);
check(
  "app_execute_blocks_expired_session",
  appOperationExecutor.includes("session?.isExpired(nowIso()) == true") &&
    appOperationExecutor.includes("App-agent session expired"),
  "direct execution blocks expired sessions"
);
check(
  "app_executor_uses_fresh_playbook_registry",
  appOperationExecutor.includes("registryProvider: () -> AppOperationPlaybookRegistry") &&
    appOperationExecutor.includes("val currentRegistry = registryProvider()") &&
    appOperationExecutor.includes("currentRegistry.forAction") &&
    appOperationExecutor.includes("currentRegistry.find") &&
    appOperationExecutor.includes("fun supportsActionType(actionType: String): Boolean") &&
    appOperationExecutor.includes("registryProvider().forAction(actionType) != null"),
  "app operation routing sees newly stored playbooks"
);
check(
  "tool_registry_routes_current_user_taught_actions",
  toolRegistry.includes("appOperationExecutor.supportsActionType(step.actionType)") &&
    toolRegistry.includes("step.input[\"__requiredSourceIds\"]") &&
    toolRegistry.includes("requiredSourceIds = requiredSourceIdsFor(step)") &&
    outdoorPlanner.includes("actionType = \"tasks.add\"") &&
    outdoorPlanner.includes("tool = \"app_agent.custom\"") &&
    outdoorPlanner.includes("\"__requiredSourceIds\" to \"google_calendar\"") &&
    outdoorPlanner.includes("taughtTaskStepIfRequested(task.goal"),
  "generic taught app actions emitted by planning route through current stored playbooks with declared source scope"
);
check(
  "app_executor_dispatches_through_live_bridge",
  appOperationLiveBridge.includes("interface AppOperationLiveBridge") &&
    appOperationLiveBridge.includes("class RecordingAppOperationLiveBridge") &&
    appOperationLiveBridge.includes("class AccessibilityQueueingLiveBridge") &&
    appOperationLiveBridge.includes("recording_simulation:") &&
    appOperationLiveBridge.includes("queued_for_accessibility") &&
    appOperationExecutor.includes("private val liveBridge: AppOperationLiveBridge") &&
    appOperationExecutor.includes("AccessibilityQueueingLiveBridge(auditLedger)") &&
    appOperationExecutor.includes("val liveResult = liveBridge.dispatch(request, playbook)") &&
    appOperationExecutor.includes("if (liveResult.status == AppOperationStatus.NEEDS_HANDOFF)") &&
    appOperationExecutor.includes("primaryActionLabel = \"Run in app\"") &&
    appOperationExecutor.includes("if (liveResult.status == AppOperationStatus.VERIFIED)") &&
    appOperationExecutor.indexOf("val liveResult = liveBridge.dispatch(request, playbook)") <
      appOperationExecutor.indexOf("approvalReceiptLedger.consume") &&
    launcherActivity.includes("liveBridge = AccessibilityQueueingLiveBridge(runtimeAuditLedger)") &&
    accessibilityService.includes("finalizeVerifiedOperation") &&
    accessibilityService.includes("approvalReceiptLedger.consume"),
  "app executor queues live work for AccessibilityService and only simulation bridges auto-verify"
);
check(
  "runtime_uses_injectable_system_clock",
  read("app/src/main/java/app/conductor/runtime/SystemClock.kt").includes("object SystemClock") &&
    read("app/src/main/java/app/conductor/runtime/SystemClock.kt").includes("fun nowIso()") &&
    appOperationExecutor.includes("SystemClock.nowIso()") &&
    accessibilityService.includes("SystemClock.nowIso()") &&
    launcherActivity.includes("SystemClock.nowIso()") &&
    launcherState.includes("SystemClock.nowIso()"),
  "sessions, handoffs, freshness, and live queue expiry use wall-clock time"
);
check(
  "live_device_connectors_for_outdoor_context",
  read("app/src/main/java/app/conductor/connectors/DeviceCalendarConnector.kt").includes("class DeviceCalendarConnector") &&
    read("app/src/main/java/app/conductor/connectors/DeviceCalendarConnector.kt").includes("CalendarContract") &&
    read("app/src/main/java/app/conductor/connectors/OpenMeteoWeatherConnector.kt").includes("class OpenMeteoWeatherConnector") &&
    read("app/src/main/java/app/conductor/connectors/OpenMeteoWeatherConnector.kt").includes("api.open-meteo.com") &&
    read("app/src/main/java/app/conductor/connectors/DeviceContactsConnector.kt").includes("class DeviceContactsConnector") &&
    mockOutdoorConnectors.includes("DeviceCalendarConnector(context)") &&
    mockOutdoorConnectors.includes("OpenMeteoWeatherConnector(context)") &&
    mockOutdoorConnectors.includes("DeviceContactsConnector(context)") &&
    conductorRuntime.includes("androidContext: Context? = null") &&
    launcherActivity.includes("androidContext = applicationContext") &&
    launcherActivity.includes("RequestMultiplePermissions()") &&
    launcherActivity.includes("READ_CALENDAR"),
  "outdoor planning hydrates live device calendar, Open-Meteo weather, and contacts with permission-aware fallbacks"
);
check(
  "launcher_supplies_record_backed_registry_provider",
  launcherActivity.includes("registryProvider =") &&
    launcherActivity.includes("customPlaybooks = recordStore.appOperationPlaybooks()"),
  "launcher executor reads current stored playbooks"
);
check(
  "accessibility_service_refreshes_whitelist_per_event",
  accessibilityService.includes("playbookRegistryProvider: () -> AppOperationPlaybookRegistry") &&
    accessibilityService.includes("val whitelistedPackages = playbookRegistryProvider().whitelistedPackages()") &&
    accessibilityService.includes("customPlaybooks = recordStore.appOperationPlaybooks()"),
  "accessibility service does not cache stale app-agent whitelist"
);
check(
  "accessibility_service_uses_live_tree_bridge",
  appOperationModels.includes("val operation: String = \"auto\"") &&
    appOperationModels.includes("val inputKey: String = \"\"") &&
    appOperationModels.includes("val recoverySelectorHints: List<String> = emptyList()") &&
    androidPreferencesRecordStore.includes(".put(\"operation\", operation)") &&
    androidPreferencesRecordStore.includes(".put(\"inputKey\", inputKey)") &&
    androidPreferencesRecordStore.includes(".put(\"recoverySelectorHints\", recoverySelectorHints.toSet().toJsonArray())") &&
    androidPreferencesRecordStore.includes("operation = optString(\"operation\", \"auto\")") &&
    androidPreferencesRecordStore.includes("inputKey = optString(\"inputKey\", \"\")") &&
    androidPreferencesRecordStore.includes("recoverySelectorHints = optJSONArray(\"recoverySelectorHints\")?.toStringSet()?.toList()?.sorted().orEmpty()") &&
    appAgentOnboarding.includes("unsupported_step_operation") &&
    appAgentOnboarding.includes("set_text_requires_input_key") &&
    appAgentOnboarding.includes("step_input_not_declared") &&
    appAgentOnboarding.includes("too_many_recovery_hints") &&
    appAgentOnboarding.includes("blank_recovery_hint") &&
    appAgentOnboarding.includes("recovery_missing") &&
    appAgentOnboarding.includes("recovery_ambiguous") &&
    appOperationPlaybookRegistry.includes("operation = \"set_text\"") &&
    appOperationPlaybookRegistry.includes("inputKey = \"body\"") &&
    appOperationPlaybookRegistry.includes("inputKey = \"exactBody\"") &&
    appOperationPlaybookRegistry.includes("recoverySelectorHints = listOf(\"new message button\", \"search conversations\")") &&
    appOperationPlaybookRegistry.includes("recoverySelectorHints = listOf(\"what's on your mind\", \"create post\")") &&
    read("app/src/main/java/app/conductor/operator/accessibility/CustomAppPlaybookSeeder.kt").includes("operation = \"set_text\"") &&
    launcherActivity.includes("operation = \"set_text\"") &&
    launcherActivity.includes("inputKey = \"title\"") &&
    launcherActivity.includes("recoverySelectorHints = authoredRecoveryLabels") &&
    launcherState.includes("recoverySelectorHints.takeIf { it.isNotEmpty() }?.let { \"recover=${it.joinToString(\"|\")}\" }") &&
    appOperationExecutor.includes("recoverySelectorHints.takeIf { it.isNotEmpty() }?.let { \"recover=${it.joinToString(\"|\")}\" }") &&
    accessibilityAppOperationLiveBridge.includes("class AccessibilityAppOperationLiveBridge") &&
    accessibilityAppOperationLiveBridge.includes("activeRootProvider: () -> AccessibilityNodeInfo?") &&
    accessibilityAppOperationLiveBridge.includes("activePackageProvider: () -> String?") &&
    accessibilityAppOperationLiveBridge.includes("foregroundLauncher: AppForegroundLauncher") &&
    accessibilityAppOperationLiveBridge.includes("foregroundLauncher.bringToForeground(request.packageName)") &&
    accessibilityAppOperationLiveBridge.includes("foreground_launch_pending:${request.packageName}") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_foreground_launch_pending") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_foreground_launch_failed") &&
    accessibilityAppOperationLiveBridge.includes("root.findAccessibilityNodeInfosByText(label)") &&
    accessibilityAppOperationLiveBridge.includes("AccessibilityNodeInfo.ACTION_SET_TEXT") &&
    accessibilityAppOperationLiveBridge.includes("AccessibilityNodeInfo.ACTION_CLICK") &&
    accessibilityAppOperationLiveBridge.includes("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE") &&
    accessibilityAppOperationLiveBridge.includes("when (step.operation)") &&
    accessibilityAppOperationLiveBridge.includes("\"set_text\" ->") &&
    accessibilityAppOperationLiveBridge.includes("\"click\" ->") &&
    accessibilityAppOperationLiveBridge.includes("step.inputKey.takeIf { it.isNotBlank() }") &&
    accessibilityAppOperationLiveBridge.includes("target.performAction") &&
    accessibilityAppOperationLiveBridge.includes("materialize(step.selectorHint, request.input)") &&
    accessibilityAppOperationLiveBridge.includes("private fun recoverAndFindTarget(") &&
    accessibilityAppOperationLiveBridge.includes("step.recoverySelectorHints") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_recovery_attempted") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_recovery_succeeded") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_recovery_handoff") &&
    accessibilityAppOperationLiveBridge.includes("active_window_missing_after_action") &&
    accessibilityAppOperationLiveBridge.includes("cancellationRequested: () -> Boolean") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_cancelled") &&
    accessibilityAppOperationLiveBridge.includes("live_operation_cancelled") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_account_proof_handoff") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_selector_handoff") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_action_handoff") &&
    accessibilityAppOperationLiveBridge.includes("operator.live_verified") &&
    appForegroundLauncher.includes("interface AppForegroundLauncher") &&
    appForegroundLauncher.includes("class AndroidAppForegroundLauncher") &&
    appForegroundLauncher.includes("context.packageManager.getLaunchIntentForPackage(packageName)") &&
    appForegroundLauncher.includes("Intent.FLAG_ACTIVITY_NEW_TASK") &&
    appForegroundLauncher.includes("context.startActivity(intent)") &&
    accessibilityService.includes("AccessibilityAppOperationLiveBridge") &&
    accessibilityService.includes("activeRootProvider = { rootInActiveWindow }") &&
    accessibilityService.includes("private var activePackageName: String? = null") &&
    accessibilityService.includes("activePackageName = packageName") &&
    accessibilityService.includes("activePackageProvider = { activePackageName }") &&
    accessibilityService.includes("foregroundLauncher = AndroidAppForegroundLauncher(applicationContext, auditLedger)") &&
    accessibilityService.includes("recordStore.queuedAppOperations()") &&
    accessibilityService.includes("recordStore.resolveQueuedAppOperation(queued.request.id)") &&
    accessibilityService.includes("accessibility.queue_resolved"),
  "accessibility service validates account proof, selectors, and post-state against the active window before resolving live app operations"
);
check(
  "accessibility_live_execution_honors_stop_autonomy",
  accessibilityService.includes("cancellationRequested = { recordStore.autonomyMode() == AutonomyMode.ASK_ONLY }") &&
    accessibilityService.includes("accessibility.autonomy_stop_observed") &&
    accessibilityService.includes("recordStore.saveAutonomyMode(AutonomyMode.ASK_ONLY)") &&
    accessibilityService.includes("recordStore.clearQueuedAppOperations()") &&
    accessibilityService.includes("accessibility.interrupted") &&
    launcherActivity.includes("recordStore.clearQueuedAppOperations()") &&
    launcherActivity.includes("autonomy.stopped"),
  "live accessibility execution observes ASK_ONLY stop state and clears queued app work on interrupt"
);
check(
  "built_in_playbooks_declare_account_proof_labels",
  appOperationPlaybookRegistry.includes("accountProofLabel = \"Messages signed in\"") &&
    appOperationPlaybookRegistry.includes("accountProofLabel = \"Calendar signed in\"") &&
    appOperationPlaybookRegistry.includes("accountProofLabel = \"Maps signed in\"") &&
    appOperationPlaybookRegistry.includes("accountProofLabel = \"Facebook signed in\"") &&
    read("app/src/main/java/app/conductor/operator/accessibility/CustomAppPlaybookSeeder.kt").includes("accountProofLabel = \"Notes signed in\"") &&
    read("app/src/main/java/app/conductor/operator/accessibility/CustomAppPlaybookSeeder.kt").includes("accountProofLabel = \"Community signed in\""),
  "built-in and seeded app-agent playbooks provide concrete visible account proof labels for live execution"
);
check(
  "record_backed_playbook_registry_provider_declared",
  appOperationPlaybookRegistry.includes("RecordBackedAppOperationPlaybookRegistryProvider") &&
    appOperationPlaybookRegistry.includes("recordStore.appOperationPlaybooks()"),
  "stored app-agent playbooks can provide a fresh registry"
);
check(
  "app_agent_onboarding_uses_label_counts",
  appAgentOnboarding.includes("observedTreeLabelCounts: Map<String, Int>") &&
    appAgentOnboarding.includes("observedTreeLabels.associateWith { 1 }") &&
    appAgentOnboarding.includes("dryRun(scopedPlaybook, observedTreeLabelCounts)"),
  "user-taught app dry-runs preserve duplicate label evidence"
);
check(
  "app_agent_onboarding_can_use_discovery_snapshots",
  appAgentOnboarding.includes("fun onboardFromDiscovery(") &&
    appAgentOnboarding.includes("discovery: AppAgentDiscovery") &&
    appAgentOnboarding.includes("playbook.packageName != discovery.packageName") &&
    appAgentOnboarding.includes("discovery.accountProofCandidates.contains(playbook.accountProofLabel)") &&
    appAgentOnboarding.includes("app_agent.discovery_onboarding_started") &&
    appAgentOnboarding.includes("observedTreeLabelCounts = discovery.visibleLabelCounts"),
  "user-taught app onboarding can dry-run against persisted foreground app discovery snapshots"
);
check(
  "app_agent_onboarding_blocks_ambiguous_targets",
  appAgentOnboarding.includes("target_ambiguous") &&
    appAgentOnboarding.includes("verifier_ambiguous") &&
    appAgentOnboarding.includes("(observedTreeLabelCounts[step.selectorHint] ?: 0) > 1") &&
    appAgentOnboarding.includes("(observedTreeLabelCounts[step.expectedState] ?: 0) > 1"),
  "ambiguous accessibility targets block app-agent onboarding"
);
check(
  "app_agent_onboarding_requires_account_proof_label",
  appOperationModels.includes("val accountProofLabel: String = \"\"") &&
    appAgentOnboarding.includes("missing_account_proof_label") &&
    appAgentOnboarding.includes("subjectLabel = scopedPlaybook.accountProofLabel") &&
    appAgentOnboarding.includes("account_proof_missing") &&
    appAgentOnboarding.includes("account_proof_ambiguous") &&
    appAgentOnboarding.includes("+\n                playbook.accountProofLabel") &&
    androidPreferencesRecordStore.includes(".put(\"accountProofLabel\", accountProofLabel)") &&
    androidPreferencesRecordStore.includes("accountProofLabel = optString(\"accountProofLabel\", \"\")") &&
    tableBlock(androidRecordStoreSchema, "app_operation_playbooks").includes("account_proof_label TEXT NOT NULL DEFAULT ''"),
  "user-taught app agents bind playbooks and login proof to a unique visible signed-in account label"
);
check(
  "app_agent_onboarding_persists_source_grants",
  appAgentOnboarding.includes("import app.conductor.graph.AppAgentGrant") &&
    appAgentOnboarding.includes("private val appAgentId: String = \"conductor.voice\"") &&
    appAgentOnboarding.includes("private val purpose: String = \"activity_planning\"") &&
    appAgentOnboarding.includes("requiredSourceIds = playbook.requiredSourceIds.ifEmpty { allowedSourceIds }") &&
    appAgentOnboarding.includes("if (allowedSourceIds.isNotEmpty())") &&
    appAgentOnboarding.includes("if (existingGrant?.revoked == true)") &&
    appAgentOnboarding.includes("app_agent.onboarding_source_grant_preserved_revoked") &&
    appAgentOnboarding.includes("recordStore.saveAppAgentGrant(") &&
    appAgentOnboarding.includes("sources = existingGrant?.sources.orEmpty() + allowedSourceIds") &&
    appAgentOnboarding.includes("expiresAtIso = expiresAtIso") &&
    appAgentOnboarding.includes("app_agent.onboarding_source_grant_created") &&
    appOperationExecutor.includes("RecordBackedAppOperationSourceAuthorizer") &&
    appOperationExecutor.includes("fun unauthorizedSourceIds(packageName: String, requiredSourceIds: Set<String>): Set<String>") &&
    appOperationExecutor.includes("packageName = playbook.packageName") &&
    appOperationExecutor.includes("packageName = request.packageName") &&
    appOperationExecutor.includes("val activeBaseSources = recordStore.graphGrants()") &&
    appOperationExecutor.includes("val activeSources = recordStore.appAgentGrants()") &&
    appOperationExecutor.includes("grant.packageName == packageName"),
  "user-taught app onboarding persists bounded package-scoped app-agent source grants that source authorization can enforce"
);
check(
  "app_playbooks_declare_persisted_source_requirements",
  appOperationModels.includes("val requiredSourceIds: Set<String> = emptySet()") &&
    appOperationPlaybookRegistry.includes("requiredSourceIds = setOf(\"device_contacts\")") &&
    appOperationPlaybookRegistry.includes("requiredSourceIds = setOf(\"google_calendar\")") &&
    appOperationPlaybookRegistry.includes("requiredSourceIds = setOf(\"maps\")") &&
    appOperationPlaybookRegistry.includes("requiredSourceIds = setOf(\"facebook_events\")") &&
    appOperationPlaybookRegistry.includes("fun all(): List<AppOperationPlaybook>") &&
    androidPreferencesRecordStore.includes(".put(\"requiredSourceIds\", requiredSourceIds.toJsonArray())") &&
    androidPreferencesRecordStore.includes("requiredSourceIds = optJSONArray(\"requiredSourceIds\")?.toStringSet() ?: emptySet()") &&
    tableBlock(androidRecordStoreSchema, "app_operation_playbooks").includes("required_source_ids_json TEXT NOT NULL DEFAULT '[]'"),
  "app-agent playbooks expose durable source requirements for launcher capability preflight"
);
check(
  "app_playbooks_route_general_voice_by_invocation_phrases",
    appOperationModels.includes("val invocationPhrases: Set<String> = emptySet()") &&
    appOperationPlaybookRegistry.includes("invocationPhrases = setOf(\"draft invite\", \"draft message\", \"write message\")") &&
    appOperationPlaybookRegistry.includes("fun forUtterance(utterance: String): AppOperationPlaybook?") &&
    appOperationPlaybookRegistry.includes("fun matchUtterance(utterance: String): AppOperationPlaybookMatch") &&
    appOperationPlaybookRegistry.includes("data class AppOperationPlaybookMatch") &&
    appOperationPlaybookRegistry.includes("isAmbiguous = matches.size > 1") &&
    appOperationPlaybookRegistry.includes("playbook.invocationPhrases.any { phrase ->") &&
    androidPreferencesRecordStore.includes(".put(\"invocationPhrases\", invocationPhrases.toJsonArray())") &&
    androidPreferencesRecordStore.includes("invocationPhrases = optJSONArray(\"invocationPhrases\")?.toStringSet() ?: emptySet()") &&
    tableBlock(androidRecordStoreSchema, "app_operation_playbooks").includes("invocation_phrases_json TEXT NOT NULL DEFAULT '[]'") &&
    conductorRuntime.includes("registry.matchUtterance(utterance)") &&
    conductorRuntime.includes("forcedPlaybookId: String = \"\"") &&
    conductorRuntime.includes("val forcedPlaybook = forcedPlaybookId.takeIf { it.isNotBlank() }?.let { registry.find(it) }") &&
    conductorRuntime.includes("\"intent.app_route_clarified\"") &&
    conductorRuntime.includes("playbookMatch?.isAmbiguous == true") &&
    conductorRuntime.includes("\"intent.app_route_ambiguous\"") &&
    conductorRuntime.includes("id = \"clarify_app_agent_route\"") &&
    conductorRuntime.includes("actionType = \"app_agent.route.clarify\"") &&
    conductorRuntime.includes("?: storedPlaybooks.firstOrNull { it.actionType.startsWith(\"app_agent.observed.\") }") &&
    appAgentOnboarding.includes("blank_invocation_phrase") &&
    launcherActivity.includes("invocationPhrases = setOf(") &&
    launcherActivity.includes("var forcedAppPlaybookId by remember") &&
    launcherActivity.includes("forcedPlaybookId = forcedAppPlaybookId") &&
    launcherActivity.includes("resolveClarifiedAppAgentRoute(") &&
    launcherActivity.includes("voice.app_route_clarification_selected") &&
    launcherActivity.includes("private data class ClarifiedAppAgentRoute") &&
    launcherActivity.includes("auditEvents.lastOrNull { it.type == \"intent.app_route_ambiguous\" }") &&
    launcherActivity.includes("val originalTask = tasks.lastOrNull { it.intentType == \"general_mobile_intent\" }") &&
    launcherActivity.includes("selected.id") &&
    launcherActivity.includes("private fun AppOperationPlaybook.matchesClarification(utterance: String): Boolean") &&
    launcherActivity.includes("authoredActionType") &&
    launcherActivity.includes("targetLabel") &&
    launcherState.includes("val invocationPhrases: List<String>") &&
    launcherState.includes("invocationPhrases = playbook.invocationPhrases.toList().sorted()") &&
    launcherScreen.includes("Say: ${appSkill.invocationPhrases.take(6).joinToString()}"),
  "stored playbooks expose invocation phrases and route matching general voice intents to the intended app-agent"
);
check(
  "app_agent_playbooks_cannot_silence_public_or_high_risk_approval",
  appAgentOnboarding.includes("fun AppOperationPlaybook.requiresExactApprovalRisk") &&
    appAgentOnboarding.includes("actionType == \"public_post.create\"") &&
    appAgentOnboarding.includes("riskLabel.contains(\"public\")") &&
    appAgentOnboarding.includes("riskLabel.startsWith(\"high_\")") &&
    appAgentOnboarding.includes("public_or_high_risk_requires_exact_approval") &&
    appAgentOnboarding.includes("public_or_high_risk_requires_exactBody") &&
    appOperationExecutor.includes("playbook.requiresExactApprovalRisk() && !playbook.requiresExactApproval") &&
    appOperationExecutor.includes("operator.unsafe_playbook_blocked") &&
    appOperationExecutor.includes("High-impact app playbook requires exact approval"),
  "public or high-risk user-taught app playbooks cannot bypass exact approval requirements"
);
check(
  "app_approval_receipt_ledger_declared",
  appOperationApprovalReceiptLedger.includes("interface AppOperationApprovalReceiptLedger") &&
    appOperationApprovalReceiptLedger.includes("fun isConsumed") &&
    appOperationApprovalReceiptLedger.includes("fun consume"),
  "single-use approval receipt ledger"
);
check(
  "app_approval_receipts_are_record_backed",
  conductorRecordStore.includes("StoredConsumedApprovalReceipt") &&
    conductorRecordStore.includes("saveConsumedApprovalReceipt") &&
    inMemoryRecordStore.includes("consumedApprovalReceiptRecords") &&
    appOperationApprovalReceiptLedger.includes("RecordBackedAppOperationApprovalReceiptLedger") &&
    appOperationApprovalReceiptLedger.includes("recordStore.saveConsumedApprovalReceipt"),
  "consumed approval receipts persist through ConductorRecordStore"
);
check(
  "app_consumed_receipts_schema_encrypted",
  androidRecordStoreSchema.includes("CREATE TABLE consumed_approval_receipts") &&
    androidRecordStoreSchema.includes("consumed_at_iso") &&
    androidStoragePlan.includes("\"consumed_approval_receipts\""),
  "consumed approval receipts have encrypted schema table"
);
check(
  "launcher_uses_record_backed_approval_receipts",
  launcherActivity.includes("RecordBackedAppOperationApprovalReceiptLedger") &&
    launcherActivity.includes("approvalReceiptLedger = RecordBackedAppOperationApprovalReceiptLedger(recordStore)"),
  "launcher app executor uses durable receipt ledger"
);
check(
  "app_exact_approval_blocks_replay",
  appOperationExecutor.includes("approvalReceiptLedger.isConsumed") &&
    appOperationExecutor.includes("operator.approval_replay_blocked") &&
    appOperationExecutor.includes("Approval receipt already used"),
  "replayed exact approval receipts queue handoff"
);
check(
  "app_exact_approval_consumed_after_verification",
  appOperationExecutor.includes("approvalReceiptLedger.consume") &&
    appOperationExecutor.includes("operator.approval_consumed") &&
    appOperationExecutor.indexOf("auditLedger.record(\"operator.verified\"") <
      appOperationExecutor.indexOf("approvalReceiptLedger.consume"),
  "approval receipt consumed after app-operation verification"
);

const screenCallbacks = [
  "onVoicePressed",
  "onStopAutonomy",
  "onAutonomySelected",
  "onAppSessionAutonomySelected",
  "onAppSessionApprovalOverrideToggled",
  "onAppSessionRevoked",
  "onAppPlaybookGrantToggled",
  "onTeachDraftActionTypeChanged",
  "onTeachDraftInputKeyChanged",
  "onTeachDraftTargetLabelChanged",
  "onTeachDraftClickLabelChanged",
  "onTeachDraftClickVerifierChanged",
  "onTeachDraftAccountProofChanged",
  "onTeachDraftRiskChanged",
  "onTeachDraftSourceScopeChanged",
  "onTeachAppAgent",
  "onDataGrantRevoked",
  "onDataGrantRestored",
  "onSourceRefresh",
  "onAppAgentGrantRevoked",
  "onAppAgentGrantRestored",
  "onApprovalApproved",
  "onApprovalDenied",
  "onAppHandoffGranted",
  "onAppHandoffCancelled"
];

for (const callback of screenCallbacks) {
  check(
    `screen_declares_callback:${callback}`,
    launcherScreen.includes(`${callback}:`),
    callback
  );
}

for (const [index, call] of callBlocks(`${launcherActivity}\n${launcherScreen}`, "ConductorLauncherScreen").entries()) {
  if (call.kind === "declaration") continue;
  for (const callback of screenCallbacks) {
    check(
      `screen_call_${index}_passes:${callback}`,
      call.body.includes(`${callback} =`),
      callback
    );
  }
}

const stateFields = [
  "title",
  "autonomyMode",
  "intentType",
  "goal",
  "transcript",
  "recommendationTitle",
  "recommendationScore",
  "appSessions",
  "appDiscoveries",
  "appCapabilities",
  "dataGrants",
  "connectorAccounts",
  "appAgentGrants",
  "sourceFreshness",
  "contextCards",
  "planSteps",
  "approvals",
  "appHandoffs",
  "appReceipts",
  "auditEvents"
];

for (const field of stateFields) {
  check(
    `state_declares_field:${field}`,
    launcherState.includes(`val ${field}:`),
    field
  );
}

for (const [index, call] of callBlocks(`${launcherState}\n${launcherScreen}`, "LauncherUiState").entries()) {
  if (call.kind === "declaration") continue;
  for (const field of stateFields) {
    check(
      `state_call_${index}_passes:${field}`,
      call.body.includes(`${field} =`),
      field
    );
  }
}

for (const arg of ["queuedAppOperations", "appOperationSessions", "appAgentDiscoveries", "graphGrants", "graphFacts", "connectorAccounts", "appAgentGrants", "durableAuditEvents", "appOperationPlaybooks"]) {
  check(
    `to_launcher_state_declares:${arg}`,
    launcherState.includes(`${arg}:`),
    arg
  );
  for (const [index, call] of callBlocks(launcherActivity, "toLauncherUiState").entries()) {
    if (call.kind === "declaration") continue;
    check(
      `to_launcher_state_call_${index}_passes:${arg}`,
      call.body.includes(`${arg} =`),
      arg
    );
  }
}

check(
  "launcher_surfaces_durable_app_receipts",
  launcherState.includes("data class AppReceiptUi") &&
    launcherState.includes("durableAuditEvents: List<AuditEvent>") &&
    launcherState.includes("val combinedAudit = (durableAuditEvents + audit).distinctBy") &&
    launcherState.includes("appReceipts = combinedAudit.toAppReceiptUi()") &&
    launcherState.includes("it.type == \"operator.execution_preview\"") &&
    launcherState.includes("it.type == \"operator.live_verified\"") &&
    launcherState.includes("it.type == \"operator.source_scope_verified\"") &&
    launcherState.includes("it.type == \"accessibility.queue_resolved\"") &&
    launcherState.includes("verified = event.type != \"operator.execution_preview\"") &&
    launcherState.includes("val stepSummaries: List<String>") &&
    launcherState.includes("val previewByRequestId = filter { it.type == \"operator.execution_preview\" }") &&
    launcherState.includes("event.detail.receiptRequestId() to event.detail.toExecutionPreviewParts()") &&
    launcherState.includes("val requestId = event.detail.receiptRequestId()") &&
    launcherState.includes("val preview = event.detail.toExecutionPreviewParts()") &&
    launcherState.includes("?: previewByRequestId[requestId]") &&
    launcherState.includes("stepSummaries = preview.stepSummaries") &&
    launcherState.includes("private fun ExecutionPreviewParts.hasReviewContext(): Boolean") &&
    launcherState.includes("private fun String.toExecutionPreviewParts()") &&
    launcherState.includes("stepSummaries = previewValue(\"steps\", listOf(\"exactApproval\")).pipeList()") &&
    launcherState.includes("private fun String.previewValue(key: String, nextKeys: List<String>): String") &&
    launcherState.includes("private fun String.receiptRequestId(): String") &&
    appOperationExecutor.includes("operator.execution_preview") &&
    appOperationExecutor.includes("executionPreview(") &&
    appOperationExecutor.includes("accountProof=${session.loginProof.method}:${session.loginProof.subjectLabel}") &&
    appOperationExecutor.includes("inputs=${request.input.toSortedMap().entries.joinToString") &&
    appOperationExecutor.includes("steps=${playbook.steps.joinToString(\"|\") { it.previewSummary() }}") &&
    appOperationExecutor.includes("private fun AppOperationStep.previewSummary()") &&
    appOperationExecutor.includes("inputKey.takeIf { it.isNotBlank() }?.let { \"input=$it\" }") &&
    appOperationExecutor.includes("exactApproval=$exactApprovalRequired:${request.approvalReceipt?.approvalId.orEmpty()}") &&
    appOperationExecutor.includes("if (liveResult.status == AppOperationStatus.VERIFIED)") &&
    appOperationExecutor.includes("operator.source_scope_verified") &&
    appOperationExecutor.indexOf("operator.execution_preview") <
      appOperationExecutor.indexOf("val liveResult = liveBridge.dispatch(request, playbook)") &&
    appOperationExecutor.includes("${request.id} ${request.packageName}:${request.requiredSourceIds.toList().sorted().joinToString()}") &&
    launcherScreen.includes("AppReceiptSection(state.appReceipts)") &&
    launcherScreen.includes("Section(title = \"App receipts\")") &&
    launcherScreen.includes("receipt.eventType == \"operator.execution_preview\" -> \"Preview\"") &&
    launcherScreen.includes("Text(\"App: ${receipt.packageName}\"") &&
    launcherScreen.includes("Text(\"Action: ${receipt.actionType}\"") &&
    launcherScreen.includes("Text(\"Steps: ${receipt.stepSummaries.joinToString(\" -> \")}\"") &&
    launcherScreen.includes("Text(\"Autonomy: ${receipt.autonomy}\"") &&
    launcherScreen.includes("Verified app operations will appear here.") &&
    launcherActivity.includes("durableAuditEvents = recordStore.auditEvents()"),
  "launcher shows durable pre-dispatch app-operation previews and parsed live-step receipts from the encrypted record store"
);

check(
  "launcher_surfaces_app_capability_preflight",
    launcherState.includes("data class AppCapabilityUi") &&
    launcherState.includes("val playbookGrantActive: Boolean") &&
    launcherState.includes("val stepSummaries: List<String>") &&
    launcherState.includes("appOperationPlaybooks: List<AppOperationPlaybook>") &&
    launcherState.includes("appCapabilities = appOperationPlaybooks.toAppCapabilityUi(") &&
    launcherState.includes("stepSummaries = playbook.steps.map { it.toStepSummary() }") &&
    launcherState.includes("private fun AppOperationStep.toStepSummary()") &&
    launcherState.includes("inputKey.takeIf { it.isNotBlank() }?.let { \"input=$it\" }") &&
    launcherState.includes("val missingBaseSources = playbook.requiredSourceIds - activeBaseSourceIds") &&
    launcherState.includes("val missingAgentSources = playbook.requiredSourceIds - activeAppAgentSourceIds") &&
    launcherState.includes("\"Needs login\" to \"No active logged-in app session.\"") &&
    launcherState.includes("playbookGrantActive = session?.allows(playbook.id) == true") &&
    launcherState.includes("\"Needs data\" to \"Restore base sources: ${missingBaseSources.sorted().joinToString()}.\"") &&
    launcherState.includes("\"Ready\" to \"Can route under ${session.autonomyMode}.\"") &&
    launcherState.includes("data class AppSkillUi") &&
    launcherState.includes("val appSkills: List<AppSkillUi>") &&
    launcherState.includes("appSkills = appCapabilities.toAppSkillUi()") &&
    launcherState.includes("private fun List<AppCapabilityUi>.toAppSkillUi()") &&
    launcherState.includes("groupBy { it.packageName }") &&
    launcherScreen.includes("onAppPlaybookGrantToggled: (String, String, Boolean) -> Unit") &&
    launcherScreen.includes("onAppPlaybookGrantToggled = onAppPlaybookGrantToggled") &&
    launcherScreen.includes("Section(title = \"App skills\")") &&
    launcherScreen.includes("appSkills.forEach { appSkill ->") &&
    launcherScreen.includes("AppSkillActionRow(") &&
    launcherScreen.includes("Playbook grant: ${if (capability.playbookGrantActive) \"Enabled\" else \"Disabled\"}") &&
    launcherScreen.includes("onAppPlaybookGrantToggled(") &&
    launcherScreen.includes("Text(if (capability.playbookGrantActive) \"Disable playbook\" else \"Enable playbook\")") &&
    launcherScreen.includes("Sources: ${appSkill.requiredSourceIds.joinToString()}") &&
    launcherScreen.includes("Steps: ${capability.stepSummaries.joinToString(\" | \")}") &&
    launcherActivity.includes("onAppPlaybookGrantToggled = { packageName, playbookId, enabled ->") &&
    launcherActivity.includes("app_agent.playbook_grant_blocked") &&
    launcherActivity.includes("session.copy(allowedPlaybookIds = nextPlaybookIds)") &&
    launcherActivity.includes("app_agent.playbook_grant_enabled") &&
    launcherActivity.includes("app_agent.playbook_grant_disabled") &&
    launcherActivity.includes(".all()"),
  "launcher shows grouped app skills with nested playbook readiness, data scope, risk, exact-approval requirements, live step operations, and per-playbook grants"
);

check(
  "accessibility_discovers_teachable_app_surfaces",
  appOperationModels.includes("data class AppAgentDiscovery") &&
    appOperationModels.includes("val visibleLabelCounts: Map<String, Int>") &&
    appOperationModels.includes("val accountProofCandidates: Set<String>") &&
    conductorRecordStore.includes("fun saveAppAgentDiscovery(discovery: AppAgentDiscovery)") &&
    conductorRecordStore.includes("fun appAgentDiscoveries(): List<AppAgentDiscovery>") &&
    inMemoryRecordStore.includes("private val appAgentDiscoveryRecords = linkedMapOf<String, AppAgentDiscovery>()") &&
    androidPreferencesRecordStore.includes("KEY_APP_AGENT_DISCOVERIES") &&
    androidPreferencesRecordStore.includes("visibleLabelCounts.toIntJsonObject()") &&
    androidPreferencesRecordStore.includes("getJSONObject(\"visibleLabelCounts\").toIntMap()") &&
    tableBlock(androidRecordStoreSchema, "app_agent_discoveries").includes("visible_label_counts_json TEXT NOT NULL") &&
    tableBlock(androidRecordStoreSchema, "app_agent_discoveries").includes("account_proof_candidates_json TEXT NOT NULL") &&
    androidStoragePlan.includes("\"app_agent_discoveries\"") &&
    accessibilityService.includes("root.toAppAgentDiscovery(packageName)") &&
    accessibilityService.includes("recordStore.saveAppAgentDiscovery(discovery)") &&
    accessibilityService.includes("app_agent.discovery_observed") &&
    accessibilityService.includes("MAX_DISCOVERY_LABELS = 40") &&
    accessibilityService.includes("MAX_DISCOVERY_LABEL_LENGTH = 48") &&
    accessibilityService.includes("if (isPassword || className?.contains(\"EditText\", ignoreCase = true) == true) return null") &&
    accessibilityService.includes("!whitelistedPackages.contains(packageName)") &&
    launcherState.includes("import app.conductor.operator.accessibility.AppAgentDiscovery") &&
    launcherState.includes("data class AppDiscoveryUi") &&
    launcherState.includes("appAgentDiscoveries: List<AppAgentDiscovery> = emptyList()") &&
    launcherState.includes("appDiscoveries = appAgentDiscoveries.map { it.toAppDiscoveryUi() }") &&
    launcherActivity.includes("appAgentDiscoveries = recordStore.appAgentDiscoveries()") &&
    launcherScreen.includes("AppDiscoverySection(discoveries = state.appDiscoveries)") &&
    launcherScreen.includes("Section(title = \"Observed apps\")") &&
    launcherScreen.includes("Account proof candidates: ${discovery.accountProofCandidates.joinToString()}"),
  "foreground accessibility observations create bounded teachable app-agent discovery snapshots without enabling execution"
);

check(
  "launcher_surfaces_cross_app_source_freshness",
  launcherState.includes("import app.conductor.connectors.ConnectedAccount") &&
  launcherState.includes("import app.conductor.graph.GraphFact") &&
    launcherState.includes("data class ConnectorAccountUi") &&
    launcherState.includes("data class SourceFreshnessUi") &&
    launcherState.includes("graphFacts: List<GraphFact> = emptyList()") &&
    launcherState.includes("connectorAccounts: List<ConnectedAccount> = emptyList()") &&
    launcherState.includes("connectorAccounts = connectorAccounts.map { it.toConnectorAccountUi() }") &&
    launcherState.includes("credentialHandle.isNotBlank()") &&
    launcherState.includes("sourceFreshness = graphFacts.toSourceFreshnessUi(graphGrants, connectorAccounts)") &&
    launcherState.includes("connectorAccounts: List<ConnectedAccount>") &&
    launcherState.includes("val sources = (map { it.source } + graphGrants.map { it.source } + connectorAccounts.map { it.source }).toSet().sorted()") &&
    launcherState.includes("connectorAccount == null -> \"Connector missing\"") &&
    launcherState.includes("connectorAccount.credentialHandle.isBlank() -> \"Credential missing\"") &&
    launcherState.includes("grant.revoked -> \"Grant revoked\"") &&
    launcherState.includes("grant.isExpired(nowIso) -> \"Grant expired\"") &&
    launcherState.includes("newestFact.isExpired(nowIso) -> \"Fact expired\"") &&
    launcherState.includes("else -> \"Fresh\"") &&
    launcherActivity.includes("graphFacts = recordStore.graphFacts()") &&
    launcherActivity.includes("connectorAccounts = recordStore.connectorAccounts()") &&
    launcherScreen.includes("connectorAccounts = state.connectorAccounts") &&
    launcherScreen.includes("StatusText(if (account.connected) \"Connected\" else \"Credential missing\")") &&
    !launcherScreen.includes("credentialHandle") &&
    launcherScreen.includes("SourceFreshnessSection(") &&
    launcherScreen.includes("freshness = state.sourceFreshness") &&
    launcherScreen.includes("onSourceRefresh = onSourceRefresh") &&
    launcherScreen.includes("Section(title = \"Source freshness\")") &&
    launcherScreen.includes("Fact expires: ${source.factExpiresAtIso}") &&
    launcherScreen.includes("Grant expires: ${source.grantExpiresAtIso}") &&
    launcherScreen.includes("OutlinedButton(onClick = { onSourceRefresh(source.source) })") &&
    launcherActivity.includes("defaultOutdoorConnectorRuntime(runtimeAuditLedger, recordStore, applicationContext).hydrateGraph(") &&
    launcherActivity.includes("outdoorPlanningRequests().filter { it.source == source }") &&
    launcherActivity.includes("connector.source_refreshed") &&
    launcherActivity.includes("connector.refresh_blocked"),
  "launcher shows and can refresh retained cross-app context sources without bypassing connector grants"
);

check(
  "launcher_can_teach_observed_app_agent",
  launcherActivity.includes("AppAgentOnboarding(runtimeAuditLedger, recordStore)") &&
    launcherState.includes("data class AppTeachDraftUi") &&
    launcherState.includes("appTeachDraft = appAgentDiscoveries.toAppTeachDraftUi(") &&
    launcherState.includes("teachDraftActionType: String = \"\"") &&
    launcherState.includes("teachDraftClickLabel: String = \"\"") &&
    launcherState.includes("teachDraftClickVerifierLabel: String = \"\"") &&
    launcherState.includes("teachDraftFieldBindingsText: String = \"\"") &&
    launcherState.includes("teachDraftRecoveryLabelsText: String = \"\"") &&
    launcherState.includes("teachDraftSourceScopeText: String = \"\"") &&
    launcherState.includes("val sourceScopeText: String") &&
    launcherState.includes("val availableSourceIds: List<String>") &&
    launcherState.includes("val unknownSourceIds: List<String>") &&
    launcherState.includes("val selectedSourceIds: List<String>") &&
    launcherState.includes("val fieldBindingSummaries: List<String>") &&
    launcherState.includes("val unknownFieldBindingLabels: List<String>") &&
    launcherState.includes("val accountProofCandidates: List<String>") &&
    launcherState.includes("val selectedRecoveryLabels: List<String>") &&
    launcherState.includes("val riskLabel: String") &&
    launcherState.includes("val exactApprovalRequired: Boolean") &&
    launcherState.includes("availableSourceIds = (graphGrants.map { it.source } + connectorAccounts.map { it.source }).toSet().sorted()") &&
    launcherState.includes("private fun List<AppAgentDiscovery>.toAppTeachDraftUi(") &&
    launcherState.includes("unknownSourceIds = sourceScopeText.toSourceIds() - availableSourceIds.toSet()") &&
    launcherState.includes("selectedSourceIds = sourceScopeText.toSourceIds().toList().sorted()") &&
    launcherState.includes("accountProofCandidates = discovery.accountProofCandidates.toList().sorted()") &&
    launcherState.includes("riskLabel = riskLabel.ifBlank { \"low_reversible\" }") &&
    launcherState.includes("val authoredRiskLabel = riskLabel.ifBlank { \"low_reversible\" }") &&
    launcherState.includes("val exactApprovalRequired = authoredRiskLabel.requiresExactApprovalRisk()") &&
    launcherState.includes("private fun String.requiresExactApprovalRisk(): Boolean") &&
    launcherState.includes("val requestedSourceIds = sourceScopeText.toSourceIds()") &&
    launcherState.includes("val unknownSourceIds = requestedSourceIds - availableSourceIds.toSet()") &&
    launcherState.includes("val fieldBindings = fieldBindingsText.toFieldBindings()") &&
    launcherState.includes("val unknownFieldBindingLabels = fieldBindings") &&
    launcherState.includes("val clickVerifier = clickVerifierLabel.ifBlank { clickLabel }") &&
    launcherState.includes("val recoveryLabels = recoveryLabelsText.toLabelSet()") &&
    launcherState.includes("val stepPreview = listOfNotNull(") &&
    launcherState.includes("recover=${it.sorted().joinToString(\"|\")}") &&
    launcherState.includes("clickLabel.takeIf { it.isNotBlank() }?.let { \"click target=$it verify=$clickVerifier\" }") &&
    launcherState.includes("unknownSourceIds.isEmpty()") &&
    launcherState.includes("unknownSourceIds.isNotEmpty() -> \"Unknown sources: ${unknownSourceIds.sorted().joinToString()}.\"") &&
    launcherState.includes("private fun String.toSourceIds(): Set<String>") &&
    launcherState.includes("private fun String.toLabelSet(): Set<String>") &&
    launcherState.includes("private fun String.toFieldBindings(): List<Pair<String, String>>") &&
    launcherScreen.includes("teachDraft: AppTeachDraftUi") &&
    launcherScreen.includes("OutlinedTextField(") &&
    launcherScreen.includes("label = { Text(\"Action type\") }") &&
    launcherScreen.includes("label = { Text(\"Input key\") }") &&
    launcherScreen.includes("label = { Text(\"Account proof label\") }") &&
    launcherScreen.includes("labels = teachDraft.accountProofCandidates") &&
    launcherScreen.includes("selected = teachDraft.accountProofLabel") &&
    launcherScreen.includes("Risk: ${teachDraft.riskLabel}") &&
    launcherScreen.includes("listOf(\"low_reversible\", \"medium_external_side_effect\", \"medium_public_side_effect\")") &&
    launcherScreen.includes("onClick = { onTeachDraftRiskChanged(risk) }") &&
    launcherScreen.includes("if (teachDraft.exactApprovalRequired)") &&
    launcherScreen.includes("Text(\"Exact approval required\"") &&
    launcherScreen.includes("label = { Text(\"Target and verifier label\") }") &&
    launcherScreen.includes("labels = teachDraft.availableLabels") &&
    launcherScreen.includes("selected = teachDraft.targetLabel") &&
    launcherScreen.includes("label = { Text(\"Extra fields\") }") &&
    launcherScreen.includes("Fields: ${teachDraft.fieldBindingSummaries.joinToString()}") &&
    launcherScreen.includes("Unknown fields: ${teachDraft.unknownFieldBindingLabels.joinToString()}") &&
    launcherScreen.includes("label = { Text(\"Click target label\") }") &&
    launcherScreen.includes("selected = teachDraft.clickLabel") &&
    launcherScreen.includes("label = { Text(\"Click verifier label\") }") &&
    launcherScreen.includes("selected = teachDraft.clickVerifierLabel") &&
    launcherScreen.includes("label = { Text(\"Recovery labels\") }") &&
    launcherScreen.includes("teachDraft.recoveryLabelsText.toggleSourceId(it)") &&
    launcherScreen.includes("Recovery: ${teachDraft.selectedRecoveryLabels.joinToString()}") &&
    launcherScreen.includes("private fun LabelChipRow(") &&
    launcherScreen.includes("labels.take(4).forEach { label ->") &&
    launcherScreen.includes("onClick = { onSelected(label) }") &&
    launcherScreen.includes("label = { Text(\"Required sources\") }") &&
    launcherScreen.includes("Available sources: ${teachDraft.availableSourceIds.joinToString()}") &&
    launcherScreen.includes("teachDraft.availableSourceIds.take(4).forEach { sourceId ->") &&
    launcherScreen.includes("selected = teachDraft.selectedSourceIds.contains(sourceId)") &&
    launcherScreen.includes("teachDraft.sourceScopeText.toggleSourceId(sourceId)") &&
    launcherScreen.includes("private fun String.toggleSourceId(sourceId: String): String") &&
    launcherScreen.includes("Unknown sources: ${teachDraft.unknownSourceIds.joinToString()}") &&
    launcherScreen.includes("Draft steps: ${teachDraft.stepPreview.joinToString(\" | \")}") &&
    launcherScreen.includes("Text(\"Dry-run and save playbook\")") &&
    launcherScreen.includes("enabled = teachDraft.canSubmit") &&
    launcherActivity.includes("var teachDraftActionType by remember") &&
    launcherActivity.includes("var teachDraftFieldBindingsText by remember") &&
    launcherActivity.includes("var teachDraftClickLabel by remember") &&
    launcherActivity.includes("var teachDraftClickVerifierLabel by remember") &&
    launcherActivity.includes("var teachDraftRecoveryLabelsText by remember") &&
    launcherActivity.includes("var teachDraftRiskLabel by remember") &&
    launcherActivity.includes("var teachDraftSourceScopeText by remember") &&
    launcherActivity.includes("teachDraftActionType = value") &&
    launcherActivity.includes("teachDraftInputKey = value") &&
    launcherActivity.includes("teachDraftTargetLabel = value") &&
    launcherActivity.includes("teachDraftFieldBindingsText = value") &&
    launcherActivity.includes("teachDraftClickLabel = value") &&
    launcherActivity.includes("teachDraftClickVerifierLabel = value") &&
    launcherActivity.includes("teachDraftRecoveryLabelsText = value") &&
    launcherActivity.includes("teachDraftAccountProofLabel = value") &&
    launcherActivity.includes("teachDraftRiskLabel = value") &&
    launcherActivity.includes("teachDraftSourceScopeText = value") &&
    launcherActivity.includes("onTeachAppAgent =") &&
    launcherActivity.includes("signed_in_account_required") &&
    launcherActivity.includes("val latestDiscovery = recordStore.appAgentDiscoveries()") &&
    launcherActivity.includes(".sortedByDescending { it.observedAtIso }") &&
    launcherActivity.includes("val accountProofLabel = teachDraftAccountProofLabel.ifBlank") &&
    launcherActivity.includes("val targetLabel = teachDraftTargetLabel.ifBlank") &&
    launcherActivity.includes("val authoredActionType = teachDraftActionType.ifBlank") &&
    launcherActivity.includes("val authoredRiskLabel = teachDraftRiskLabel.ifBlank { \"low_reversible\" }") &&
    launcherActivity.includes("val exactApprovalRequired = authoredRiskLabel.contains(\"public\")") &&
    launcherActivity.includes("authoredRiskLabel.contains(\"public\")") &&
    launcherActivity.includes("authoredRiskLabel.startsWith(\"high_\")") &&
    launcherActivity.includes("authoredActionType == \"public_post.create\"") &&
    launcherActivity.includes("val authoredInputKey = if (exactApprovalRequired)") &&
    launcherActivity.includes("val authoredSourceIds = teachDraftSourceScopeText") &&
    launcherActivity.includes(".split(\",\")") &&
    launcherActivity.includes(".filter { it.isNotBlank() }") &&
    launcherActivity.includes("val knownSourceIds = (") &&
    launcherActivity.includes("recordStore.graphGrants().map { it.source }") &&
    launcherActivity.includes("recordStore.connectorAccounts().map { it.source }") &&
    launcherActivity.includes("val unknownSourceIds = authoredSourceIds - knownSourceIds") &&
    launcherActivity.includes("val authoredFieldBindings = teachDraftFieldBindingsText.toFieldBindings()") &&
    launcherActivity.includes("val authoredRequiredInputKeys = (setOf(authoredInputKey) + authoredFieldBindings.map { it.second }).toSet()") &&
    launcherActivity.includes("val authoredClickLabel = teachDraftClickLabel") &&
    launcherActivity.includes("val authoredClickVerifierLabel = teachDraftClickVerifierLabel.ifBlank { authoredClickLabel }") &&
    launcherActivity.includes("val authoredRecoveryLabels = teachDraftRecoveryLabelsText.toLabelSet().toList().sorted()") &&
    launcherActivity.includes("appAgentOnboarding.onboardFromDiscovery(") &&
    launcherActivity.includes("discovery = latestDiscovery") &&
    launcherActivity.includes("actionType = authoredActionType") &&
    launcherActivity.includes("riskLabel = authoredRiskLabel") &&
    launcherActivity.includes("requiresExactApproval = exactApprovalRequired") &&
    launcherActivity.includes("requiredInputKeys = authoredRequiredInputKeys") &&
    launcherActivity.includes("requiredSourceIds = authoredSourceIds") &&
    launcherActivity.includes("steps = listOfNotNull(") &&
    launcherActivity.includes("id = \"fill_observed_field_${index + 1}\"") &&
    launcherActivity.includes("inputKey = authoredInputKey") &&
    launcherActivity.includes("recoverySelectorHints = authoredRecoveryLabels") &&
    launcherActivity.includes("authoredClickLabel.takeIf { it.isNotBlank() }?.let") &&
    launcherActivity.includes("id = \"commit_observed_action\"") &&
    launcherActivity.includes("operation = \"click\"") &&
    launcherActivity.includes("app_agent.teach_source_scope_blocked") &&
    launcherActivity.includes("allowedSourceIds = authoredSourceIds") &&
    launcherActivity.includes("app_agent.teach_discovery_result") &&
    launcherActivity.includes("app_agent.teach_discovery_blocked") &&
    launcherActivity.includes("id = \"tasks_add_from_voice\"") &&
    launcherActivity.includes("actionType = \"tasks.add\"") &&
    launcherActivity.includes("accountProofLabel = \"Tasks signed in\"") &&
    launcherActivity.includes("requiredInputKeys = setOf(\"title\")") &&
    launcherActivity.includes("observedTreeLabelCounts = mapOf(") &&
    launcherActivity.includes("app_agent.teach_demo_result") &&
    appAgentOnboarding.includes("recordStore.saveAppOperationPlaybook(scopedPlaybook)") &&
    appAgentOnboarding.includes("observed_accessibility_tree_dry_run") &&
    launcherScreen.includes("onTeachAppAgent: () -> Unit"),
  "launcher teaching lets the user author an observed-app playbook draft, validates it against discovery, and falls back to the signed-in demo dry-run"
);

const failed = checks.filter((item) => item.status !== "ok");
const result = {
  status: failed.length === 0 ? "ok" : "failed",
  checks: process.argv.includes("--verbose") || failed.length > 0 ? checks : undefined,
  failedChecks: failed,
  totalChecks: checks.length,
  kotlinFiles: ktFiles.length
};

console.log(JSON.stringify(result, null, 2));

if (failed.length > 0) {
  process.exit(1);
}

function check(name, ok, detail) {
  checks.push({ name, status: ok ? "ok" : "failed", detail });
}

function checkBalanced(name, body, open, close) {
  let depth = 0;
  for (const char of stripCommentsAndStrings(body)) {
    if (char === open) depth += 1;
    if (char === close) depth -= 1;
    if (depth < 0) break;
  }
  check(name, depth === 0, `${open}${close}`);
}

function callBlocks(body, name) {
  const blocks = [];
  let index = 0;
  while (index < body.length) {
    const start = body.indexOf(`${name}(`, index);
    if (start === -1) break;
    const previousChar = start > 0 ? body[start - 1] : "";
    if (/[A-Za-z0-9_]/.test(previousChar)) {
      index = start + name.length + 1;
      continue;
    }
    const prefix = body.slice(Math.max(0, start - 24), start);
    const openIndex = start + name.length;
    const closeIndex = findMatchingParen(body, openIndex);
    if (closeIndex !== -1) {
      blocks.push({
        kind: /\b(fun|class|data\s+class)\s+$/.test(prefix) ? "declaration" : "call",
        body: body.slice(openIndex + 1, closeIndex)
      });
    }
    index = start + name.length + 1;
  }
  return blocks;
}

function findMatchingParen(body, openIndex) {
  let depth = 0;
  const stripped = stripCommentsAndStrings(body);
  for (let index = openIndex; index < stripped.length; index += 1) {
    if (stripped[index] === "(") depth += 1;
    if (stripped[index] === ")") depth -= 1;
    if (depth === 0) return index;
  }
  return -1;
}

function manifestClasses(body) {
  return [...body.matchAll(/android:name="([^"]+)"/g)]
    .map((match) => match[1])
    .filter((name) => name.startsWith(".") || name.startsWith("app.conductor."));
}

function xmlResourceReferences(body) {
  return [...body.matchAll(/@([a-zA-Z0-9_]+)\/([A-Za-z0-9_.]+)/g)]
    .map((match) => `${match[1]}/${match[2]}`);
}

function resourceExists(reference) {
  const [type, name] = reference.split("/");
  if (type === "xml") {
    return fs.existsSync(path.join(resRoot, "xml", `${name}.xml`));
  }
  if (type === "string") {
    return read("app/src/main/res/values/strings.xml").includes(`name="${name}"`);
  }
  if (type === "style") {
    return read("app/src/main/res/values/styles.xml").includes(`name="${name}"`);
  }
  return true;
}

function tableBlock(body, tableName) {
  const start = body.indexOf(`CREATE TABLE ${tableName}`);
  if (start === -1) return "";
  const next = body.indexOf('""".trimIndent()', start);
  return next === -1 ? body.slice(start) : body.slice(start, next);
}

function stripCommentsAndStrings(body) {
  return body
    .replace(/"""[\s\S]*?"""/g, spaces)
    .replace(/"(?:\\.|[^"\\])*"/g, spaces)
    .replace(/\/\*[\s\S]*?\*\//g, spaces)
    .replace(/\/\/.*$/gm, spaces);
}

function spaces(match) {
  return match.replace(/[^\n]/g, " ");
}

function walk(dir) {
  if (!fs.existsSync(dir)) return [];
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(fullPath) : [fullPath];
  });
}

function read(relativePath) {
  return readAbsolute(path.join(root, relativePath));
}

function readAbsolute(fullPath) {
  if (!fs.existsSync(fullPath)) return "";
  return fs.readFileSync(fullPath, "utf8");
}
