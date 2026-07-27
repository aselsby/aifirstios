package app.conductor.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.conductor.account.AccountSessionMobileAuthTokenProvider
import app.conductor.account.RecordBackedAccountSessionStore
import app.conductor.audit.AuditLedger
import app.conductor.connectors.defaultOutdoorConnectorRuntime
import app.conductor.connectors.outdoorPlanningRequests
import app.conductor.graph.AppAgentGrant
import app.conductor.graph.PersonalGraphStore
import app.conductor.operator.accessibility.AccessibilityQueueingLiveBridge
import app.conductor.operator.accessibility.AppAgentOnboarding
import app.conductor.operator.accessibility.AppOperationApprovalReceipt
import app.conductor.operator.accessibility.AppOperationExecutor
import app.conductor.operator.accessibility.AppOperationInputRepair
import app.conductor.operator.accessibility.AppLoginState
import app.conductor.operator.accessibility.AppLoginProof
import app.conductor.operator.accessibility.AppOperationPlaybook
import app.conductor.operator.accessibility.AppOperationSession
import app.conductor.operator.accessibility.AppOperationPlaybookRegistry
import app.conductor.operator.accessibility.AppOperationStep
import app.conductor.operator.accessibility.AppOperationStatus
import app.conductor.operator.accessibility.CustomAppPlaybookSeeder
import app.conductor.operator.accessibility.RecordBackedAppOperationApprovalReceiptLedger
import app.conductor.operator.accessibility.RecordBackedAppOperationQueue
import app.conductor.operator.accessibility.RecordBackedAppOperationSessionStore
import app.conductor.operator.accessibility.RecordBackedAppOperationSourceAuthorizer
import app.conductor.policy.ApprovalDecisionStore
import app.conductor.policy.UserPolicyStore
import app.conductor.runtime.AuditEvent
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.ConductorRuntime
import app.conductor.runtime.SystemClock
import app.conductor.runtime.Task
import app.conductor.storage.AndroidConductorRecordStoreFactory
import app.conductor.tools.ToolRegistry
import app.conductor.tools.intents.AndroidContextIntentLauncher
import app.conductor.ui.ConductorLauncherScreen
import app.conductor.ui.toLauncherUiState
import app.conductor.voice.AndroidSpeechOutput
import app.conductor.voice.AndroidSpeechCapture
import app.conductor.voice.FallbackRealtimeSessionTokenProvider
import app.conductor.voice.HttpRealtimeSessionTokenProvider
import app.conductor.voice.ProductionRealtimeModelTransport
import app.conductor.voice.RecordingRealtimeSessionTokenProvider
import app.conductor.voice.VoiceControlCommandParser
import app.conductor.voice.VoiceSessionController
import app.conductor.voice.VoiceHandoffRunner

class ConductorLauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val recordStore = remember {
                AndroidConductorRecordStoreFactory.create(applicationContext)
            }
            remember { CustomAppPlaybookSeeder.seedDefaults(recordStore) }
            val accountSessionStore = remember { RecordBackedAccountSessionStore(recordStore) }
            val accountSession = accountSessionStore.currentSession()
            val policyStore = remember { UserPolicyStore(applicationContext, recordStore) }
            val approvalDecisionStore = remember { ApprovalDecisionStore(applicationContext, recordStore) }
            val voiceSessionController = remember { VoiceSessionController() }
            val voiceControlCommandParser = remember { VoiceControlCommandParser() }
            val voiceHandoffRunner = remember {
                VoiceHandoffRunner(
                    controller = voiceSessionController,
                    speechCapture = AndroidSpeechCapture(applicationContext),
                    speechOutput = AndroidSpeechOutput(applicationContext),
                    realtimeModelTransport = ProductionRealtimeModelTransport(
                        sessionTokenProvider = FallbackRealtimeSessionTokenProvider(
                            primary = HttpRealtimeSessionTokenProvider(
                                authTokenProvider = AccountSessionMobileAuthTokenProvider(accountSessionStore)
                            ),
                            fallback = RecordingRealtimeSessionTokenProvider()
                        )
                    )
                )
            }
            val runtimeAuditLedger = remember { AuditLedger(recordStore) }
            val appOperationInputRepair = remember { AppOperationInputRepair(runtimeAuditLedger) }
            val appAgentOnboarding = remember { AppAgentOnboarding(runtimeAuditLedger, recordStore) }
            val appOperationExecutor = remember {
                AppOperationExecutor(
                    auditLedger = runtimeAuditLedger,
                    registryProvider = {
                        AppOperationPlaybookRegistry(
                            customPlaybooks = recordStore.appOperationPlaybooks()
                        )
                    },
                    sessionStore = RecordBackedAppOperationSessionStore(recordStore),
                    operationQueue = RecordBackedAppOperationQueue(recordStore),
                    approvalReceiptLedger = RecordBackedAppOperationApprovalReceiptLedger(recordStore),
                    sourceAuthorizer = RecordBackedAppOperationSourceAuthorizer(recordStore),
                    // Never verify through recording simulation on the product launcher path.
                    liveBridge = AccessibilityQueueingLiveBridge(runtimeAuditLedger)
                )
            }
            val runtime = remember {
                ConductorRuntime(
                    auditLedger = runtimeAuditLedger,
                    toolRegistry = ToolRegistry(
                        auditLedger = runtimeAuditLedger,
                        appOperationExecutor = appOperationExecutor,
                        androidIntentLauncher = AndroidContextIntentLauncher(applicationContext)
                    ),
                    recordStore = recordStore,
                    androidContext = applicationContext
                )
            }
            var userPolicy by remember {
                mutableStateOf(policyStore.load())
            }
            var utterance by remember {
                mutableStateOf("Find me something outdoors to do this afternoon and draft an invite to Maya.")
            }
            var mobileIntentType by remember {
                mutableStateOf("outdoor_activity")
            }
            var forcedAppPlaybookId by remember {
                mutableStateOf("")
            }
            var teachDraftActionType by remember {
                mutableStateOf("")
            }
            var teachDraftInputKey by remember {
                mutableStateOf("title")
            }
            var teachDraftTargetLabel by remember {
                mutableStateOf("")
            }
            var teachDraftFieldBindingsText by remember {
                mutableStateOf("")
            }
            var teachDraftClickLabel by remember {
                mutableStateOf("")
            }
            var teachDraftClickVerifierLabel by remember {
                mutableStateOf("")
            }
            var teachDraftRecoveryLabelsText by remember {
                mutableStateOf("")
            }
            var teachDraftAccountProofLabel by remember {
                mutableStateOf("")
            }
            var teachDraftRiskLabel by remember {
                mutableStateOf("low_reversible")
            }
            var teachDraftSourceScopeText by remember {
                mutableStateOf("")
            }
            var microphonePermissionGranted by remember {
                mutableStateOf(checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            }
            var decisionVersion by remember { mutableStateOf(0) }
            val startVoiceCapture = {
                runtimeAuditLedger.record("voice.permission_granted", "Microphone permission available.")
                voiceHandoffRunner.startMobileIntentCapture(
                    autonomyMode = userPolicy.mode.name
                ) { handoff ->
                    utterance = handoff.utterance
                    mobileIntentType = handoff.intentType
                    forcedAppPlaybookId = ""
                    val voiceControlCommand = voiceControlCommandParser.parse(handoff.utterance)
                    if (voiceControlCommand != null) {
                        if (voiceControlCommand.kind == "set_global_autonomy" && voiceControlCommand.autonomyMode != null) {
                            userPolicy = policyStore.saveMode(voiceControlCommand.autonomyMode)
                            if (voiceControlCommand.stopAllAutonomy) {
                                recordStore.appOperationSessions().forEach { session ->
                                    recordStore.saveAppOperationSession(
                                        session.copy(autonomyMode = AutonomyMode.ASK_ONLY)
                                    )
                                }
                                recordStore.clearQueuedAppOperations()
                                voiceHandoffRunner.cancel("voice_control_stop_autonomy")
                            }
                            runtimeAuditLedger.record(
                                "voice.control_global_autonomy_applied",
                                "${voiceControlCommand.autonomyMode}:stopAll=${voiceControlCommand.stopAllAutonomy}"
                            )
                            decisionVersion += 1
                            return@startMobileIntentCapture
                        }
                        if (voiceControlCommand.kind == "set_action_approval_override") {
                            val session = recordStore.appOperationSessions().firstOrNull {
                                it.userId == accountSession?.userId &&
                                    it.packageName == voiceControlCommand.packageName
                            }
                            if (session != null) {
                                val approvalRequiredActions = session.approvalRequiredActionTypes.toMutableSet()
                                if (voiceControlCommand.requireApproval) {
                                    approvalRequiredActions.add(voiceControlCommand.actionType)
                                } else {
                                    approvalRequiredActions.remove(voiceControlCommand.actionType)
                                }
                                recordStore.saveAppOperationSession(
                                    session.copy(approvalRequiredActionTypes = approvalRequiredActions)
                                )
                                runtimeAuditLedger.record(
                                    "voice.control_action_approval_applied",
                                    "${voiceControlCommand.packageName}:${voiceControlCommand.actionType}:${voiceControlCommand.requireApproval}"
                                )
                            } else {
                                runtimeAuditLedger.record(
                                    "voice.control_action_approval_blocked",
                                    "${voiceControlCommand.packageName}:${voiceControlCommand.actionType}:session_missing"
                                )
                            }
                            decisionVersion += 1
                            return@startMobileIntentCapture
                        }
                        if (voiceControlCommand.kind == "cancel_pending_app_work") {
                            val activeUserId = accountSessionStore.currentSession()?.userId
                            val cancellable = recordStore.queuedAppOperations()
                                .filterNot { queued ->
                                    queued.isExpired(SystemClock.nowIso()).also { expired ->
                                        if (expired) {
                                            recordStore.resolveQueuedAppOperation(queued.request.id)
                                            runtimeAuditLedger.record("operator.queue_expired", queued.request.id)
                                        }
                                    }
                                }
                                .filter { queued ->
                                    activeUserId != null &&
                                        queued.request.userId == activeUserId &&
                                        (
                                            voiceControlCommand.cancelAllPending ||
                                                queued.request.packageName == voiceControlCommand.packageName ||
                                                queued.request.input["__actionType"] == voiceControlCommand.actionType ||
                                                AppOperationPlaybookRegistry(
                                                    customPlaybooks = recordStore.appOperationPlaybooks()
                                                ).find(queued.request.playbookId)?.actionType == voiceControlCommand.actionType
                                            )
                                }
                            cancellable.forEach { queued ->
                                recordStore.resolveQueuedAppOperation(queued.request.id)
                            }
                            runtimeAuditLedger.record(
                                if (cancellable.isEmpty()) {
                                    "voice.control_pending_cancel_blocked"
                                } else {
                                    "voice.control_pending_cancelled"
                                },
                                "${voiceControlCommand.packageName}:${voiceControlCommand.actionType}:count=${cancellable.size}"
                            )
                            decisionVersion += 1
                            return@startMobileIntentCapture
                        }
                    }
                    val clarifiedRoute = resolveClarifiedAppAgentRoute(
                        utterance = handoff.utterance,
                        tasks = recordStore.tasks(),
                        auditEvents = recordStore.auditEvents(),
                        playbooks = AppOperationPlaybookRegistry(
                            customPlaybooks = recordStore.appOperationPlaybooks()
                        ).all()
                    )
                    if (clarifiedRoute != null) {
                        utterance = clarifiedRoute.originalUtterance
                        mobileIntentType = "general_mobile_intent"
                        forcedAppPlaybookId = clarifiedRoute.playbookId
                        runtimeAuditLedger.record(
                            "voice.app_route_clarification_selected",
                            "${clarifiedRoute.playbookId}:${handoff.utterance}"
                        )
                        decisionVersion += 1
                        return@startMobileIntentCapture
                    }
                    val queuedExactApproval = recordStore.queuedAppOperations()
                        .filterNot { queued ->
                            queued.isExpired(SystemClock.nowIso()).also { expired ->
                                if (expired) {
                                    recordStore.resolveQueuedAppOperation(queued.request.id)
                                    runtimeAuditLedger.record("operator.queue_expired", queued.request.id)
                                }
                            }
                        }
                        .firstOrNull { it.primaryActionLabel == "Approve exact content" }
                    if (queuedExactApproval != null) {
                        val activeUserId = accountSessionStore.currentSession()?.userId
                        if (
                            activeUserId != null &&
                            queuedExactApproval.request.userId == activeUserId &&
                            handoff.utterance.looksLikeExactContentRevision()
                        ) {
                            val revised = appOperationInputRepair.reviseExactContentFromUtterance(
                                queued = queuedExactApproval,
                                utterance = handoff.utterance
                            )
                            if (revised.filledInputKeys.contains("exactBody")) {
                                recordStore.enqueueAppOperation(
                                    queuedExactApproval.copy(request = revised.request)
                                )
                                runtimeAuditLedger.record(
                                    "operator.exact_content_revision_pending_approval",
                                    queuedExactApproval.request.id
                                )
                                decisionVersion += 1
                                return@startMobileIntentCapture
                            }
                        } else if (handoff.utterance.looksLikeExactContentRevision()) {
                            runtimeAuditLedger.record(
                                "operator.exact_content_revision_blocked",
                                "${queuedExactApproval.request.id}:${queuedExactApproval.request.userId}:${activeUserId.orEmpty()}"
                            )
                            decisionVersion += 1
                            return@startMobileIntentCapture
                        }
                    }
                    val queuedReview = recordStore.queuedAppOperations()
                        .filterNot { queued ->
                            queued.isExpired(SystemClock.nowIso()).also { expired ->
                                if (expired) {
                                    recordStore.resolveQueuedAppOperation(queued.request.id)
                                    runtimeAuditLedger.record("operator.queue_expired", queued.request.id)
                                }
                            }
                        }
                        .firstOrNull { it.primaryActionLabel == "Review inputs" }
                    if (queuedReview != null) {
                        val playbook = AppOperationPlaybookRegistry(
                            customPlaybooks = recordStore.appOperationPlaybooks()
                        ).find(queuedReview.request.playbookId)
                        val activeUserId = accountSessionStore.currentSession()?.userId
                        if (
                            playbook != null &&
                            activeUserId != null &&
                            queuedReview.request.userId == activeUserId
                        ) {
                            val repaired = appOperationInputRepair.repairFromUtterance(
                                queued = queuedReview,
                                playbook = playbook,
                                utterance = handoff.utterance
                            )
                            if (repaired.filledInputKeys.isNotEmpty()) {
                                val execution = appOperationExecutor.execute(repaired.request)
                                if (execution.status == AppOperationStatus.VERIFIED) {
                                    recordStore.resolveQueuedAppOperation(queuedReview.request.id)
                                    runtimeAuditLedger.record(
                                        "operator.input_repair_retry_verified",
                                        "${queuedReview.request.id}:${repaired.filledInputKeys.sorted().joinToString()}"
                                    )
                                } else {
                                    runtimeAuditLedger.record(
                                        "operator.input_repair_retry_pending",
                                        "${queuedReview.request.id}:${execution.status}:${repaired.missingInputKeys.sorted().joinToString()}"
                                    )
                                }
                            }
                        } else {
                            runtimeAuditLedger.record(
                                "operator.input_repair_blocked",
                                "${queuedReview.request.id}:${queuedReview.request.userId}:${activeUserId.orEmpty()}"
                            )
                        }
                    }
                }
                decisionVersion += 1
            }
            val microphonePermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                microphonePermissionGranted = granted
                if (granted) {
                    startVoiceCapture()
                } else {
                    runtimeAuditLedger.record("voice.permission_denied", "Microphone permission denied.")
                    voiceSessionController.interrupt("microphone_permission_denied")
                    decisionVersion += 1
                }
            }
            val contextPermissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) { grants ->
                runtimeAuditLedger.record(
                    "context.permissions_result",
                    grants.entries.sortedBy { it.key }.joinToString(",") { "${it.key.substringAfterLast('.')}=${it.value}" }
                )
                decisionVersion += 1
            }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val needed = listOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CONTACTS
                ).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                if (needed.isNotEmpty()) {
                    runtimeAuditLedger.record(
                        "context.permissions_requested",
                        needed.joinToString(",") { it.substringAfterLast('.') }
                    )
                    contextPermissionsLauncher.launch(needed.toTypedArray())
                }
            }
            val result = runtime.runMobileIntentWorkflow(
                intentType = mobileIntentType,
                policy = userPolicy,
                approvedApprovalIds = approvalDecisionStore.approvedIds(),
                approvedApprovalDecisions = approvalDecisionStore.approvedDecisions(),
                deniedApprovalIds = approvalDecisionStore.deniedIds(),
                userId = accountSession?.userId ?: "signed_out",
                utterance = utterance,
                forcedPlaybookId = forcedAppPlaybookId
            )

            ConductorLauncherScreen(
                state = result.toLauncherUiState(
                    queuedAppOperations = recordStore.queuedAppOperations().filterNot { queued ->
                        queued.isExpired(SystemClock.nowIso()).also { expired ->
                            if (expired) {
                                recordStore.resolveQueuedAppOperation(queued.request.id)
                                runtimeAuditLedger.record("operator.queue_expired", queued.request.id)
                            }
                        }
                    },
                    appOperationSessions = recordStore.appOperationSessions(),
                    appAgentDiscoveries = recordStore.appAgentDiscoveries(),
                    graphGrants = recordStore.graphGrants(),
                    graphFacts = recordStore.graphFacts(),
                    connectorAccounts = recordStore.connectorAccounts(),
                    appAgentGrants = recordStore.appAgentGrants(),
                    operationTimelines = recordStore.operationTimelines(),
                    durableAuditEvents = recordStore.auditEvents(),
                    appOperationPlaybooks = AppOperationPlaybookRegistry(
                        customPlaybooks = recordStore.appOperationPlaybooks()
                    ).all(),
                    teachDraftActionType = teachDraftActionType,
                    teachDraftInputKey = teachDraftInputKey,
                    teachDraftTargetLabel = teachDraftTargetLabel,
                    teachDraftFieldBindingsText = teachDraftFieldBindingsText,
                    teachDraftClickLabel = teachDraftClickLabel,
                    teachDraftClickVerifierLabel = teachDraftClickVerifierLabel,
                    teachDraftRecoveryLabelsText = teachDraftRecoveryLabelsText,
                    teachDraftAccountProofLabel = teachDraftAccountProofLabel,
                    teachDraftRiskLabel = teachDraftRiskLabel,
                    teachDraftSourceScopeText = teachDraftSourceScopeText
                ),
                onVoicePressed = {
                    if (microphonePermissionGranted) {
                        startVoiceCapture()
                    } else {
                        runtimeAuditLedger.record("voice.permission_requested", "Requesting microphone permission.")
                        microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        decisionVersion += 1
                    }
                },
                onStopAutonomy = {
                    userPolicy = policyStore.saveMode(AutonomyMode.ASK_ONLY)
                    recordStore.appOperationSessions().forEach { session ->
                        recordStore.saveAppOperationSession(
                            session.copy(
                                autonomyMode = AutonomyMode.ASK_ONLY,
                                remainingAutonomousActions = 0
                            )
                        )
                    }
                    recordStore.clearQueuedAppOperations()
                    voiceHandoffRunner.cancel("autonomy_stopped_by_user")
                    runtimeAuditLedger.record(
                        "autonomy.stopped",
                        "Global and app-session autonomy set to ASK_ONLY; queued app operations cleared."
                    )
                    decisionVersion += 1
                },
                onAutonomySelected = { mode ->
                    userPolicy = policyStore.saveMode(mode)
                    decisionVersion += 1
                },
                onAppSessionAutonomySelected = { packageName, mode ->
                    val session = recordStore.appOperationSessions().firstOrNull {
                        it.userId == accountSession?.userId && it.packageName == packageName
                    }
                    if (session != null) {
                        recordStore.saveAppOperationSession(
                            session.copy(
                                autonomyMode = mode,
                                remainingAutonomousActions = if (mode == AutonomyMode.ASK_ONLY) 0 else 3
                            )
                        )
                        runtimeAuditLedger.record(
                            "operator.session_autonomy_updated",
                            "$packageName:$mode:budget=${if (mode == AutonomyMode.ASK_ONLY) 0 else 3}"
                        )
                        decisionVersion += 1
                    }
                },
                onAppSessionApprovalOverrideToggled = { packageName, actionType ->
                    val session = recordStore.appOperationSessions().firstOrNull {
                        it.userId == accountSession?.userId && it.packageName == packageName
                    }
                    if (session != null) {
                        val approvalRequiredActions = session.approvalRequiredActionTypes.toMutableSet()
                        if (approvalRequiredActions.contains(actionType)) {
                            approvalRequiredActions.remove(actionType)
                        } else {
                            approvalRequiredActions.add(actionType)
                        }
                        recordStore.saveAppOperationSession(
                            session.copy(approvalRequiredActionTypes = approvalRequiredActions)
                        )
                        runtimeAuditLedger.record(
                            "operator.session_approval_override_updated",
                            "$packageName:$actionType:${approvalRequiredActions.contains(actionType)}"
                        )
                        decisionVersion += 1
                    }
                },
                onAppSessionRevoked = { packageName ->
                    val session = recordStore.appOperationSessions().firstOrNull {
                        it.userId == accountSession?.userId && it.packageName == packageName
                    }
                    if (session != null) {
                        recordStore.saveAppOperationSession(
                            session.copy(
                                revoked = true,
                                loginState = AppLoginState.LOGGED_OUT,
                                allowedPlaybookIds = emptySet(),
                                allowedSourceIds = emptySet(),
                                approvalRequiredActionTypes = emptySet(),
                                remainingAutonomousActions = 0
                            )
                        )
                        runtimeAuditLedger.record("app_agent.session_revoked", packageName)
                        decisionVersion += 1
                    }
                },
                onAppPlaybookGrantToggled = { packageName, playbookId, enabled ->
                    val activeUserId = accountSessionStore.currentSession()?.userId
                    val session = recordStore.appOperationSessions().firstOrNull {
                        it.userId == activeUserId && it.packageName == packageName
                    }
                    if (
                        activeUserId == null ||
                        session == null ||
                        session.revoked ||
                        session.loginState != AppLoginState.LOGGED_IN ||
                        !session.hasLoginProof()
                    ) {
                        runtimeAuditLedger.record(
                            "app_agent.playbook_grant_blocked",
                            "$packageName:$playbookId:login_required"
                        )
                    } else {
                        val nextPlaybookIds = if (enabled) {
                            session.allowedPlaybookIds + playbookId
                        } else {
                            session.allowedPlaybookIds - playbookId
                        }
                        recordStore.saveAppOperationSession(
                            session.copy(allowedPlaybookIds = nextPlaybookIds)
                        )
                        runtimeAuditLedger.record(
                            if (enabled) "app_agent.playbook_grant_enabled" else "app_agent.playbook_grant_disabled",
                            "$packageName:$playbookId"
                        )
                    }
                    decisionVersion += 1
                },
                onTeachDraftActionTypeChanged = { value ->
                    teachDraftActionType = value
                    decisionVersion += 1
                },
                onTeachDraftInputKeyChanged = { value ->
                    teachDraftInputKey = value
                    decisionVersion += 1
                },
                onTeachDraftTargetLabelChanged = { value ->
                    teachDraftTargetLabel = value
                    decisionVersion += 1
                },
                onTeachDraftFieldBindingsChanged = { value ->
                    teachDraftFieldBindingsText = value
                    decisionVersion += 1
                },
                onTeachDraftClickLabelChanged = { value ->
                    teachDraftClickLabel = value
                    decisionVersion += 1
                },
                onTeachDraftClickVerifierChanged = { value ->
                    teachDraftClickVerifierLabel = value
                    decisionVersion += 1
                },
                onTeachDraftRecoveryLabelsChanged = { value ->
                    teachDraftRecoveryLabelsText = value
                    decisionVersion += 1
                },
                onTeachDraftAccountProofChanged = { value ->
                    teachDraftAccountProofLabel = value
                    decisionVersion += 1
                },
                onTeachDraftRiskChanged = { value ->
                    teachDraftRiskLabel = value
                    decisionVersion += 1
                },
                onTeachDraftSourceScopeChanged = { value ->
                    teachDraftSourceScopeText = value
                    decisionVersion += 1
                },
                onTeachAppAgent = {
                    val activeUserId = accountSessionStore.currentSession()?.userId
                    if (activeUserId == null) {
                        runtimeAuditLedger.record("app_agent.teach_blocked", "signed_in_account_required")
                    } else {
                        val latestDiscovery = recordStore.appAgentDiscoveries()
                            .sortedByDescending { it.observedAtIso }
                            .firstOrNull()
                        if (latestDiscovery != null) {
                            val accountProofLabel = teachDraftAccountProofLabel.ifBlank {
                                latestDiscovery.accountProofCandidates.firstOrNull().orEmpty()
                            }
                            val targetLabel = teachDraftTargetLabel.ifBlank {
                                latestDiscovery.visibleLabelCounts.entries
                                    .firstOrNull {
                                        it.value == 1 && !latestDiscovery.accountProofCandidates.contains(it.key)
                                    }
                                    ?.key.orEmpty()
                            }
                            val authoredActionType = teachDraftActionType.ifBlank {
                                "app_agent.observed.${latestDiscovery.packageName}"
                            }
                            val authoredRiskLabel = teachDraftRiskLabel.ifBlank { "low_reversible" }
                            val exactApprovalRequired = authoredRiskLabel.contains("public") ||
                                authoredRiskLabel.startsWith("high_") ||
                                authoredActionType == "public_post.create" ||
                                authoredActionType.contains("public")
                            val authoredInputKey = if (exactApprovalRequired) {
                                "exactBody"
                            } else {
                                teachDraftInputKey.ifBlank { "title" }
                            }
                            val authoredSourceIds = teachDraftSourceScopeText
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotBlank() }
                                .toSet()
                            val authoredRecoveryLabels = teachDraftRecoveryLabelsText.toLabelSet().toList().sorted()
                            val authoredFieldBindings = teachDraftFieldBindingsText.toFieldBindings()
                            val authoredRequiredInputKeys = (setOf(authoredInputKey) + authoredFieldBindings.map { it.second }).toSet()
                            val knownSourceIds = (
                                recordStore.graphGrants().map { it.source } +
                                    recordStore.connectorAccounts().map { it.source }
                                ).toSet()
                            val unknownSourceIds = authoredSourceIds - knownSourceIds
                            val authoredClickLabel = teachDraftClickLabel
                            val authoredClickVerifierLabel = teachDraftClickVerifierLabel.ifBlank { authoredClickLabel }
                            if (accountProofLabel.isBlank() || targetLabel.isBlank()) {
                                runtimeAuditLedger.record(
                                    "app_agent.teach_discovery_blocked",
                                    "${latestDiscovery.packageName}:accountProof=$accountProofLabel:target=$targetLabel"
                                )
                            } else if (unknownSourceIds.isNotEmpty()) {
                                runtimeAuditLedger.record(
                                    "app_agent.teach_source_scope_blocked",
                                    "${latestDiscovery.packageName}:${unknownSourceIds.sorted().joinToString()}"
                                )
                            } else {
                                val result = appAgentOnboarding.onboardFromDiscovery(
                                    userId = activeUserId,
                                    appName = latestDiscovery.packageName,
                                    discovery = latestDiscovery,
                                    playbook = AppOperationPlaybook(
                                        id = "observed_${latestDiscovery.packageName.replace(".", "_")}_${authoredActionType.replace(".", "_")}",
                                        packageName = latestDiscovery.packageName,
                                        actionType = authoredActionType,
                                        riskLabel = authoredRiskLabel,
                                        requiresExactApproval = exactApprovalRequired,
                                        invocationPhrases = setOf(
                                            authoredActionType,
                                            authoredInputKey,
                                            targetLabel,
                                            authoredClickLabel
                                        ).filter { it.isNotBlank() }.toSet(),
                                        accountProofLabel = accountProofLabel,
                                        requiredInputKeys = authoredRequiredInputKeys,
                                        requiredSourceIds = authoredSourceIds,
                                        steps = listOfNotNull(
                                            AppOperationStep(
                                                id = "focus_observed_target",
                                                description = "Focus the observed app target.",
                                                selectorHint = targetLabel,
                                                expectedState = targetLabel,
                                                operation = "set_text",
                                                inputKey = authoredInputKey,
                                                recoverySelectorHints = authoredRecoveryLabels
                                            ),
                                            *authoredFieldBindings.mapIndexed { index, binding ->
                                                AppOperationStep(
                                                    id = "fill_observed_field_${index + 1}",
                                                    description = "Fill an observed app field.",
                                                    selectorHint = binding.first,
                                                    expectedState = binding.first,
                                                    operation = "set_text",
                                                    inputKey = binding.second
                                                )
                                            }.toTypedArray(),
                                            authoredClickLabel.takeIf { it.isNotBlank() }?.let {
                                                AppOperationStep(
                                                    id = "commit_observed_action",
                                                    description = "Commit the observed app action.",
                                                    selectorHint = it,
                                                    expectedState = authoredClickVerifierLabel,
                                                    operation = "click"
                                                )
                                            }
                                        )
                                    ),
                                    allowedSourceIds = authoredSourceIds,
                                    autonomyMode = AutonomyMode.DRAFT_ONLY
                                )
                                runtimeAuditLedger.record("app_agent.teach_discovery_result", result.detail)
                            }
                        } else {
                            val result = appAgentOnboarding.onboard(
                                userId = activeUserId,
                                appName = "Tasks",
                                playbook = AppOperationPlaybook(
                                    id = "tasks_add_from_voice",
                                    packageName = "com.example.tasks",
                                    actionType = "tasks.add",
                                    riskLabel = "low_reversible",
                                    requiresExactApproval = false,
                                    invocationPhrases = setOf("task", "todo", "remind", "add task"),
                                    accountProofLabel = "Tasks signed in",
                                    requiredInputKeys = setOf("title"),
                                    steps = listOf(
                                        AppOperationStep(
                                            id = "focus_task_title",
                                            description = "Focus the task title field.",
                                            selectorHint = "Task title",
                                            expectedState = "Task title",
                                            operation = "set_text",
                                            inputKey = "title"
                                        )
                                    )
                                ),
                                allowedSourceIds = setOf("google_calendar"),
                                autonomyMode = AutonomyMode.DRAFT_ONLY,
                                observedTreeLabelCounts = mapOf(
                                    "Tasks signed in" to 1,
                                    "Task title" to 1
                                )
                            )
                            runtimeAuditLedger.record("app_agent.teach_demo_result", result.detail)
                        }
                    }
                    decisionVersion += 1
                },
                onDataGrantRevoked = { grantId ->
                    val grant = recordStore.graphGrants().firstOrNull { it.id == grantId }
                    if (grant != null) {
                        recordStore.saveGraphGrant(grant.copy(revoked = true))
                        runtimeAuditLedger.record("graph.grant_revoked", "${grant.source}:${grant.purposes.joinToString()}")
                        decisionVersion += 1
                    }
                },
                onDataGrantRestored = { grantId ->
                    val grant = recordStore.graphGrants().firstOrNull { it.id == grantId }
                    if (grant != null) {
                        recordStore.saveGraphGrant(grant.copy(revoked = false))
                        runtimeAuditLedger.record("graph.grant_restored", "${grant.source}:${grant.purposes.joinToString()}")
                        decisionVersion += 1
                    }
                },
                onSourceRefresh = { source ->
                    val requests = outdoorPlanningRequests().filter { it.source == source }
                    if (requests.isEmpty()) {
                        runtimeAuditLedger.record("connector.refresh_blocked", "$source:unsupported_source")
                    } else {
                        defaultOutdoorConnectorRuntime(runtimeAuditLedger, recordStore, applicationContext).hydrateGraph(
                            graph = PersonalGraphStore(runtimeAuditLedger, recordStore),
                            requests = requests
                        )
                        runtimeAuditLedger.record("connector.source_refreshed", source)
                    }
                    decisionVersion += 1
                },
                onAppAgentGrantRevoked = { grantId ->
                    val grant = recordStore.appAgentGrants().firstOrNull { it.id == grantId }
                    if (grant != null) {
                        recordStore.saveAppAgentGrant(grant.copy(revoked = true))
                        runtimeAuditLedger.record("app_agent.grant_revoked", "${grant.appAgentId}:${grant.sources.joinToString()}")
                        decisionVersion += 1
                    }
                },
                onAppAgentGrantRestored = { grantId ->
                    val grant = recordStore.appAgentGrants().firstOrNull { it.id == grantId }
                    if (grant != null) {
                        recordStore.saveAppAgentGrant(grant.copy(revoked = false))
                        runtimeAuditLedger.record("app_agent.grant_restored", "${grant.appAgentId}:${grant.sources.joinToString()}")
                        decisionVersion += 1
                    }
                },
                onApprovalApproved = { approvalId ->
                    val approval = result.firstPassResults
                        .mapNotNull { it.approval }
                        .firstOrNull { it.id == approvalId }
                    if (approval != null) {
                        approvalDecisionStore.approve(approval)
                    } else {
                        approvalDecisionStore.approve(approvalId)
                    }
                    decisionVersion += 1
                },
                onApprovalDenied = { approvalId ->
                    approvalDecisionStore.deny(approvalId)
                    decisionVersion += 1
                },
                onAppHandoffGranted = { requestId ->
                    val queued = recordStore.queuedAppOperations().firstOrNull { it.request.id == requestId }
                    if (queued != null) {
                        if (queued.isExpired(SystemClock.nowIso())) {
                            recordStore.resolveQueuedAppOperation(requestId)
                            runtimeAuditLedger.record("operator.handoff_expired", requestId)
                            decisionVersion += 1
                        } else {
                        val activeUserId = accountSessionStore.currentSession()?.userId
                        if (
                            activeUserId == null ||
                            queued.request.userId == "signed_out" ||
                            queued.request.userId != activeUserId
                        ) {
                            runtimeAuditLedger.record(
                                "operator.handoff_account_mismatch",
                                "${queued.request.id}:${queued.request.userId}:${activeUserId.orEmpty()}"
                            )
                            decisionVersion += 1
                        } else if (queued.primaryActionLabel == "Review inputs") {
                            runtimeAuditLedger.record(
                                "operator.handoff_review_required",
                                "${queued.request.id}:${queued.primaryActionLabel}"
                            )
                            decisionVersion += 1
                        } else if (queued.primaryActionLabel == "Restore data access") {
                            val restoredSourceIds = queued.request.requiredSourceIds
                            val restoredGrantExpiresAtIso = "2026-07-28T10:45:00-05:00"
                            val missingBaseSourceIds = restoredSourceIds.filter { sourceId ->
                                recordStore.graphGrants().none {
                                    !it.revoked &&
                                        !it.isExpired(SystemClock.nowIso()) &&
                                        it.source == sourceId &&
                                        it.purposes.contains("activity_planning")
                                }
                            }.toSet()
                            if (missingBaseSourceIds.isNotEmpty()) {
                                val restorableBaseGrants = missingBaseSourceIds.mapNotNull { sourceId ->
                                    recordStore.graphGrants().firstOrNull {
                                        it.source == sourceId &&
                                            it.purposes.contains("activity_planning")
                                    }
                                }
                                val restorableSourceIds = restorableBaseGrants.map { it.source }.toSet()
                                val unconnectedSourceIds = missingBaseSourceIds - restorableSourceIds
                                if (unconnectedSourceIds.isNotEmpty()) {
                                    runtimeAuditLedger.record(
                                        "graph.grant_connect_required",
                                        "${queued.request.id}:${unconnectedSourceIds.joinToString()}"
                                    )
                                    decisionVersion += 1
                                } else {
                                    restorableBaseGrants.forEach { grant ->
                                        recordStore.saveGraphGrant(
                                            grant.copy(
                                                purposes = grant.purposes + "activity_planning",
                                                revoked = false,
                                                expiresAtIso = restoredGrantExpiresAtIso
                                            )
                                        )
                                    }
                                    runtimeAuditLedger.record(
                                        "graph.grant_restored_from_handoff",
                                        "${queued.request.id}:${restorableSourceIds.joinToString()}"
                                    )
                                }
                            }
                            val remainingMissingBaseSourceIds = restoredSourceIds.filter { sourceId ->
                                recordStore.graphGrants().none {
                                    !it.revoked &&
                                        !it.isExpired(SystemClock.nowIso()) &&
                                        it.source == sourceId &&
                                        it.purposes.contains("activity_planning")
                                }
                            }.toSet()
                            if (remainingMissingBaseSourceIds.isEmpty()) {
                                val appAgentGrantId = "agent_grant_${queued.request.packageName}_${queued.request.playbookId}"
                                    .replace(".", "_")
                                val existingGrant = recordStore.appAgentGrants().firstOrNull {
                                    it.appAgentId == "conductor.voice" &&
                                        it.packageName == queued.request.packageName &&
                                        it.purposes.contains("activity_planning")
                                }
                                val restoredGrant = existingGrant?.copy(
                                    sources = existingGrant.sources + restoredSourceIds,
                                    revoked = false,
                                    expiresAtIso = restoredGrantExpiresAtIso
                                ) ?: AppAgentGrant(
                                    id = appAgentGrantId,
                                    appAgentId = "conductor.voice",
                                    packageName = queued.request.packageName,
                                    purposes = setOf("activity_planning"),
                                    sources = restoredSourceIds,
                                    expiresAtIso = restoredGrantExpiresAtIso
                                )
                                recordStore.saveAppAgentGrant(restoredGrant)
                                runtimeAuditLedger.record(
                                    "app_agent.grant_restored_from_handoff",
                                    "${restoredGrant.packageName}:${restoredSourceIds.joinToString()}"
                                )
                                val execution = appOperationExecutor.execute(queued.request)
                                if (execution.status == AppOperationStatus.VERIFIED) {
                                    recordStore.resolveQueuedAppOperation(requestId)
                                    runtimeAuditLedger.record(
                                        "operator.source_restore_retry_verified",
                                        "${queued.request.id}:${execution.verification?.method.orEmpty()}"
                                    )
                                } else {
                                    runtimeAuditLedger.record(
                                        "operator.source_restore_retry_pending",
                                        "${queued.request.id}:${execution.status}"
                                    )
                                }
                                decisionVersion += 1
                            } else {
                                runtimeAuditLedger.record(
                                    "graph.grant_restore_required",
                                    "${queued.request.id}:${remainingMissingBaseSourceIds.joinToString()}"
                                )
                                decisionVersion += 1
                            }
                        } else if (queued.primaryActionLabel == "Renew autonomy budget") {
                            val existingSession = recordStore.appOperationSessions().firstOrNull {
                                it.userId == queued.request.userId && it.packageName == queued.request.packageName
                            }
                            if (existingSession == null) {
                                runtimeAuditLedger.record(
                                    "operator.autonomy_budget_renewal_blocked",
                                    "${queued.request.id}:missing_session"
                                )
                                decisionVersion += 1
                            } else {
                                recordStore.saveAppOperationSession(
                                    existingSession.copy(remainingAutonomousActions = 3)
                                )
                                runtimeAuditLedger.record(
                                    "operator.autonomy_budget_renewed",
                                    "${queued.request.packageName}:3"
                                )
                                val execution = appOperationExecutor.execute(queued.request)
                                if (execution.status == AppOperationStatus.VERIFIED) {
                                    recordStore.resolveQueuedAppOperation(requestId)
                                    runtimeAuditLedger.record(
                                        "operator.autonomy_budget_retry_verified",
                                        "${queued.request.id}:${execution.verification?.method.orEmpty()}"
                                    )
                                } else {
                                    runtimeAuditLedger.record(
                                        "operator.autonomy_budget_retry_pending",
                                        "${queued.request.id}:${execution.status}"
                                    )
                                }
                                decisionVersion += 1
                            }
                        } else if (queued.primaryActionLabel == "Approve exact content") {
                            val playbook = AppOperationPlaybookRegistry(
                                customPlaybooks = recordStore.appOperationPlaybooks()
                            ).find(queued.request.playbookId)
                            val exactContent = queued.request.input.exactApprovalContent()
                            if (playbook == null || exactContent.isBlank()) {
                                runtimeAuditLedger.record(
                                    "operator.exact_handoff_unresolved",
                                    "${queued.request.id}:missing playbook or exact content"
                                )
                                decisionVersion += 1
                            } else {
                                val receipt = AppOperationApprovalReceipt(
                                    approvalId = "exact_${queued.request.id}_${playbook.actionType}_${exactContent.hashCode()}",
                                    actionType = playbook.actionType,
                                    approvedExactContent = exactContent
                                )
                                val execution = appOperationExecutor.execute(
                                    queued.request.copy(approvalReceipt = receipt)
                                )
                                if (execution.status == AppOperationStatus.VERIFIED) {
                                    recordStore.resolveQueuedAppOperation(requestId)
                                    runtimeAuditLedger.record(
                                        "operator.exact_handoff_approved",
                                        "${queued.request.id}:${receipt.approvalId}"
                                    )
                                } else {
                                    runtimeAuditLedger.record(
                                        "operator.exact_handoff_still_blocked",
                                        "${queued.request.id}:${execution.status}"
                                    )
                                }
                                decisionVersion += 1
                            }
                        } else {
                            val existingSession = recordStore.appOperationSessions().firstOrNull {
                                it.userId == queued.request.userId && it.packageName == queued.request.packageName
                            }
                            val loginProof = existingSession?.loginProof ?: AppLoginProof(
                                method = "user_confirmed_app_handoff",
                                subjectLabel = queued.request.packageName,
                                verifiedAtIso = SystemClock.nowIso()
                            )
                            val renewedSessionExpiresAtIso = "2026-07-27T18:00:00-05:00"
                            val session = existingSession ?: AppOperationSession(
                                userId = queued.request.userId,
                                packageName = queued.request.packageName,
                                loginState = AppLoginState.LOGGED_IN,
                                autonomyMode = userPolicy.mode,
                                allowedPlaybookIds = emptySet(),
                                allowedSourceIds = emptySet(),
                                loginProof = loginProof,
                                expiresAtIso = renewedSessionExpiresAtIso
                            )
                            val queuedPlaybook = AppOperationPlaybookRegistry(
                                customPlaybooks = recordStore.appOperationPlaybooks()
                            ).find(queued.request.playbookId)
                            val approvalRequiredActions = if (queuedPlaybook?.requiresExactApproval == true) {
                                session.approvalRequiredActionTypes + queuedPlaybook.actionType
                            } else {
                                session.approvalRequiredActionTypes
                            }
                            recordStore.saveAppOperationSession(
                                session.copy(
                                    loginState = AppLoginState.LOGGED_IN,
                                    autonomyMode = userPolicy.mode,
                                    allowedPlaybookIds = session.allowedPlaybookIds + queued.request.playbookId,
                                    allowedSourceIds = session.allowedSourceIds + queued.request.requiredSourceIds,
                                    approvalRequiredActionTypes = approvalRequiredActions,
                                    remainingAutonomousActions = 3,
                                    loginProof = loginProof,
                                    revoked = false,
                                    expiresAtIso = renewedSessionExpiresAtIso
                                )
                            )
                            runtimeAuditLedger.record(
                                "operator.session_granted",
                                "${queued.request.packageName}:${queued.request.playbookId}:${queued.primaryActionLabel}"
                            )
                            val execution = appOperationExecutor.execute(queued.request)
                            if (execution.status == AppOperationStatus.VERIFIED) {
                                recordStore.resolveQueuedAppOperation(requestId)
                                runtimeAuditLedger.record(
                                    "operator.handoff_grant_retry_verified",
                                    "${queued.request.id}:${execution.verification?.method.orEmpty()}"
                                )
                            } else {
                                runtimeAuditLedger.record(
                                    "operator.handoff_grant_retry_pending",
                                    "${queued.request.id}:${execution.status}"
                                )
                            }
                            decisionVersion += 1
                        }
                        }
                    }
                },
                onAppHandoffCancelled = { requestId ->
                    val queued = recordStore.queuedAppOperations().firstOrNull { it.request.id == requestId }
                    val activeUserId = accountSessionStore.currentSession()?.userId
                    if (
                        queued != null &&
                        activeUserId != null &&
                        queued.request.userId == activeUserId
                    ) {
                        recordStore.resolveQueuedAppOperation(requestId)
                        runtimeAuditLedger.record(
                            "operator.handoff_cancelled_by_user",
                            "${queued.request.id}:${queued.request.packageName}:${queued.request.playbookId}"
                        )
                    } else {
                        runtimeAuditLedger.record(
                            "operator.handoff_cancel_blocked",
                            "$requestId:${queued?.request?.userId.orEmpty()}:${activeUserId.orEmpty()}"
                        )
                    }
                    decisionVersion += 1
                }
            )

            decisionVersion
        }
    }
}

private fun Map<String, String>.exactApprovalContent(): String =
    get("exactBody")
        ?: get("body")
        ?: get("title")
        ?: toSortedMap().entries.joinToString { "${it.key}=${it.value}" }

private fun String.looksLikeExactContentRevision(): Boolean {
    val normalized = lowercase()
    return normalized.contains("change it to") ||
        normalized.contains("change the text to") ||
        normalized.contains("revise it to") ||
        normalized.contains("exact text is") ||
        normalized.contains("make it ") ||
        normalized.startsWith("say ") ||
        normalized.startsWith("post ") ||
        normalized.startsWith("send ")
}

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

private data class ClarifiedAppAgentRoute(
    val originalUtterance: String,
    val playbookId: String
)

private fun resolveClarifiedAppAgentRoute(
    utterance: String,
    tasks: List<Task>,
    auditEvents: List<AuditEvent>,
    playbooks: List<AppOperationPlaybook>
): ClarifiedAppAgentRoute? {
    val lastAmbiguousRoute = auditEvents.lastOrNull { it.type == "intent.app_route_ambiguous" }
        ?: return null
    val originalTask = tasks.lastOrNull { it.intentType == "general_mobile_intent" }
        ?: return null
    val candidates = lastAmbiguousRoute.detail
        .split("|")
        .mapNotNull { candidate ->
            val parts = candidate.split(":")
            if (parts.size < 2) return@mapNotNull null
            val packageName = parts[0]
            val actionType = parts.drop(1).joinToString(":")
            playbooks.firstOrNull {
                it.packageName == packageName &&
                    it.actionType == actionType
            }
        }
    val selected = candidates.firstOrNull { it.matchesClarification(utterance) }
        ?: return null
    return ClarifiedAppAgentRoute(
        originalUtterance = originalTask.goal,
        playbookId = selected.id
    )
}

private fun AppOperationPlaybook.matchesClarification(utterance: String): Boolean {
    val normalized = utterance.lowercase()
    val packageTokens = packageName.split(".").filter { it.length > 2 }
    val actionTokens = actionType.split(".").filter { it.length > 2 }
    return normalized.contains(id.lowercase()) ||
        packageTokens.any { normalized.contains(it.lowercase()) } ||
        actionTokens.any { normalized.contains(it.lowercase()) } ||
        invocationPhrases.any { normalized.contains(it.lowercase()) }
}
