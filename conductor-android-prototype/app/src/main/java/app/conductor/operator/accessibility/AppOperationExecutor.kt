package app.conductor.operator.accessibility

import app.conductor.audit.AuditLedger
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore

class AppOperationExecutor(
    private val auditLedger: AuditLedger,
    private val registry: AppOperationPlaybookRegistry = AppOperationPlaybookRegistry(),
    private val registryProvider: () -> AppOperationPlaybookRegistry = { registry },
    private val sessionStore: AppOperationSessionStore = InMemoryAppOperationSessionStore(),
    private val operationQueue: AppOperationQueue = InMemoryAppOperationQueue(),
    private val approvalReceiptLedger: AppOperationApprovalReceiptLedger = InMemoryAppOperationApprovalReceiptLedger(),
    private val sourceAuthorizer: AppOperationSourceAuthorizer = AllowAllAppOperationSourceAuthorizer(),
    // Production path queues for AccessibilityService. Recording bridge is opt-in for tests only.
    private val liveBridge: AppOperationLiveBridge = AccessibilityQueueingLiveBridge(auditLedger),
    private val nowIso: () -> String = { SystemClock.nowIso() }
) {
    fun routeAction(
        userId: String,
        actionType: String,
        requiredSourceIds: Set<String> = emptySet()
    ): AppAgentRoute {
        if (!hasSignedInUser(userId)) {
            return blockedRoute(actionType, "signed_in_account_required")
        }

        val currentRegistry = registryProvider()
        val playbook = currentRegistry.forAction(actionType)
            ?: return blockedRoute(actionType, "No app agent supports $actionType")

        if (!currentRegistry.whitelistedPackages().contains(playbook.packageName)) {
            return blockedRoute(actionType, "Package ${playbook.packageName} is not whitelisted", playbook)
        }

        val session = sessionStore.sessionFor(userId, playbook.packageName)
        val surface = surfaceFor(playbook, session)
        if (session?.revoked == true) {
            return blockedRoute(actionType, "app_agent_revoked", playbook)
        }

        if (session == null || session.loginState != AppLoginState.LOGGED_IN || !session.hasLoginProof()) {
            auditLedger.record("agent_route.needs_login", "$actionType -> ${playbook.packageName}")
            return AppAgentRoute(
                status = AppAgentRouteStatus.NEEDS_LOGIN,
                actionType = actionType,
                surface = surface,
                playbook = playbook,
                reason = "login_required"
            )
        }

        if (session.isExpired(nowIso())) {
            auditLedger.record("agent_route.session_expired", "$actionType -> ${playbook.packageName}:${session.expiresAtIso}")
            return AppAgentRoute(
                status = AppAgentRouteStatus.NEEDS_LOGIN,
                actionType = actionType,
                surface = surface,
                playbook = playbook,
                reason = "session_expired"
            )
        }

        if (!session.allows(playbook.id)) {
            auditLedger.record("agent_route.needs_grant", "$actionType -> ${playbook.packageName}:${playbook.id}")
            return AppAgentRoute(
                status = AppAgentRouteStatus.NEEDS_GRANT,
                actionType = actionType,
                surface = surface,
                playbook = playbook,
                reason = "playbook_grant_required"
            )
        }

        val missingSourceIds = requiredSourceIds.filter { !session.allowedSourceIds.contains(it) }.toSet()
        if (missingSourceIds.isNotEmpty()) {
            auditLedger.record("agent_route.needs_grant", "$actionType missing sources ${missingSourceIds.joinToString()}")
            return AppAgentRoute(
                status = AppAgentRouteStatus.NEEDS_GRANT,
                actionType = actionType,
                surface = surface,
                playbook = playbook,
                reason = "source_grant_required",
                missingSourceIds = missingSourceIds
            )
        }

        val unauthorizedSourceIds = sourceAuthorizer.unauthorizedSourceIds(
            packageName = playbook.packageName,
            requiredSourceIds = requiredSourceIds
        )
        if (unauthorizedSourceIds.isNotEmpty()) {
            auditLedger.record("agent_route.needs_source_restore", "$actionType unauthorized sources ${unauthorizedSourceIds.joinToString()}")
            return AppAgentRoute(
                status = AppAgentRouteStatus.NEEDS_GRANT,
                actionType = actionType,
                surface = surface,
                playbook = playbook,
                reason = "app_agent_source_grant_required",
                missingSourceIds = unauthorizedSourceIds
            )
        }

        auditLedger.record("agent_route.ready", "$actionType -> ${playbook.packageName}:${playbook.id}")
        return AppAgentRoute(
            status = AppAgentRouteStatus.READY,
            actionType = actionType,
            surface = surface,
            playbook = playbook
        )
    }

    fun executeRouted(
        actionType: String,
        input: Map<String, String>,
        approvalReceipt: AppOperationApprovalReceipt? = null,
        userId: String = "user_001",
        requiredSourceIds: Set<String> = emptySet()
    ): AppOperationResult {
        val route = routeAction(
            userId = userId,
            actionType = actionType,
            requiredSourceIds = requiredSourceIds
        )
        val playbook = route.playbook
        val surface = route.surface
        if (route.status != AppAgentRouteStatus.READY || playbook == null || surface == null) {
            return resultForRouteHandoff(route, input, approvalReceipt, userId)
        }

        return execute(
            AppOperationRequest(
                id = "operation_${actionType.replace(".", "_")}",
                userId = userId,
                packageName = surface.packageName,
                playbookId = playbook.id,
                approvalReceipt = approvalReceipt,
                requiredSourceIds = requiredSourceIds,
                input = input
            )
        )
    }

    fun execute(request: AppOperationRequest): AppOperationResult {
        if (!hasSignedInUser(request.userId)) {
            return blocked(request, "Signed-in Conductor account required for app operation")
        }

        val currentRegistry = registryProvider()
        val playbook = currentRegistry.find(request.playbookId)
            ?: return blocked(request, "Missing playbook ${request.playbookId}")

        if (request.packageName != playbook.packageName) {
            return blocked(request, "Package ${request.packageName} does not match ${playbook.packageName}")
        }

        if (!currentRegistry.whitelistedPackages().contains(request.packageName)) {
            return blocked(request, "Package ${request.packageName} is not whitelisted")
        }

        if (playbook.requiresExactApprovalRisk() && !playbook.requiresExactApproval) {
            auditLedger.record("operator.unsafe_playbook_blocked", "${request.id}:${playbook.id}:${playbook.riskLabel}")
            return blocked(request, "High-impact app playbook requires exact approval")
        }

        val session = sessionStore.sessionFor(request.userId, request.packageName)
        if (session?.isExpired(nowIso()) == true) {
            return queueForUserAction(
                request = request,
                reason = "App-agent session expired for ${request.packageName}",
                requiredUserAction = "Open ${request.packageName}, sign in, and renew this app-agent grant.",
                primaryActionLabel = "Renew session"
            )
        }
        if (session?.allows(playbook.id) != true) {
            return queueForUserAction(
                request = request,
                reason = if (session?.revoked == true) "App-agent session revoked for ${request.packageName}" else "No logged-in app session with grant for ${playbook.id}",
                requiredUserAction = "Open ${request.packageName}, sign in, and grant this playbook to Conductor.",
                primaryActionLabel = "Confirm login and grant"
            )
        }
        val unauthorizedSourceIds = sourceAuthorizer.unauthorizedSourceIds(
            packageName = request.packageName,
            requiredSourceIds = request.requiredSourceIds
        )
        if (unauthorizedSourceIds.isNotEmpty()) {
            return queueForUserAction(
                request = request,
                reason = "App-agent data grant missing or revoked for ${unauthorizedSourceIds.joinToString()}",
                requiredUserAction = "Restore agent data access for ${unauthorizedSourceIds.joinToString()} before Conductor can operate this app.",
                primaryActionLabel = "Restore data access"
            )
        }

        val requiredInputs = requiredInputsFor(playbook)
        val missingInputs = requiredInputs.filter { request.input[it].isNullOrBlank() }
        if (missingInputs.isNotEmpty()) {
            return queueForUserAction(
                request = request,
                reason = "Missing or ambiguous input: ${missingInputs.joinToString()}",
                requiredUserAction = "Confirm exact app-operation inputs before execution.",
                primaryActionLabel = "Review inputs"
            )
        }

        val exactApprovalRequired = playbook.requiresExactApproval || session.requiresApprovalFor(playbook.actionType)
        if (exactApprovalRequired) {
            val approvalReceipt = request.approvalReceipt
            val exactBody = exactApprovalContent(request)
            if (
                approvalReceipt == null ||
                approvalReceipt.actionType != playbook.actionType ||
                approvalReceipt.approvedExactContent != exactBody
            ) {
                return queueForUserAction(
                    request = request,
                    reason = "Exact approval receipt missing or mismatched for ${playbook.actionType}",
                    requiredUserAction = "Approve the exact content again before Conductor can operate this app.",
                    primaryActionLabel = "Approve exact content",
                    autonomyContext = "mode=${session.autonomyMode}; action=${playbook.actionType}; sessionApprovalOverride=${session.requiresApprovalFor(playbook.actionType)}"
                )
            }
            if (approvalReceiptLedger.isConsumed(approvalReceipt.approvalId)) {
                auditLedger.record("operator.approval_replay_blocked", "${request.id}:${approvalReceipt.approvalId}")
                return queueForUserAction(
                    request = request,
                    reason = "Approval receipt already used for ${playbook.actionType}",
                    requiredUserAction = "Approve the exact content again before Conductor can operate this app.",
                    primaryActionLabel = "Approve exact content",
                    autonomyContext = "mode=${session.autonomyMode}; action=${playbook.actionType}; sessionApprovalOverride=${session.requiresApprovalFor(playbook.actionType)}"
                )
            }
            auditLedger.record("operator.exact_approval_verified", "${request.id}:${approvalReceipt.approvalId}")
        }

        val autonomyDecision = evaluateSessionAutonomy(session, playbook, exactApprovalRequired)
        if (!autonomyDecision.allowed) {
            return queueForUserAction(
                request = request,
                reason = autonomyDecision.reason,
                requiredUserAction = "Raise the app session autonomy or approve this operation manually.",
                primaryActionLabel = "Apply autonomy and retry",
                autonomyContext = "mode=${session.autonomyMode}; risk=${playbook.riskLabel}; exactApproval=$exactApprovalRequired; sessionApprovalOverride=${session.requiresApprovalFor(playbook.actionType)}"
            )
        }
        if (!exactApprovalRequired && session.remainingAutonomousActions <= 0) {
            return queueForUserAction(
                request = request,
                reason = "Autonomy action budget exhausted for ${request.packageName}",
                requiredUserAction = "Review this app-agent session before Conductor continues autonomous app work.",
                primaryActionLabel = "Renew autonomy budget",
                autonomyContext = "mode=${session.autonomyMode}; budget=${session.remainingAutonomousActions}; action=${playbook.actionType}"
            )
        }
        auditLedger.record("operator.autonomy_checked", "${request.id}:${autonomyDecision.auditDetail}")
        auditLedger.record(
            "operator.execution_preview",
            executionPreview(
                request = request,
                playbook = playbook,
                session = session,
                autonomyDecision = autonomyDecision,
                exactApprovalRequired = exactApprovalRequired
            )
        )

        // Live bridges own the operator.verified post_state_receipt after active app-tree checks.
        val liveResult = liveBridge.dispatch(request, playbook)
        if (liveResult.status == AppOperationStatus.NEEDS_HANDOFF) {
            return queueForUserAction(
                request = request,
                reason = "Live app operation needs foreground app verification: ${liveResult.detail}",
                requiredUserAction = "Open ${request.packageName} and keep the verified account visible so Conductor can continue.",
                primaryActionLabel = "Run in app",
                autonomyContext = "mode=${session.autonomyMode}; action=${playbook.actionType}; foreground=${request.packageName}"
            )
        }
        if (liveResult.status == AppOperationStatus.VERIFIED) {
            if (request.requiredSourceIds.isNotEmpty()) {
                auditLedger.record(
                    "operator.source_scope_verified",
                    "${request.id} ${request.packageName}:${request.requiredSourceIds.toList().sorted().joinToString()}"
                )
            }
            request.approvalReceipt?.let { receipt ->
                approvalReceiptLedger.consume(receipt)
                auditLedger.record("operator.approval_consumed", "${request.id}:${receipt.approvalId}")
            }
            if (!exactApprovalRequired) {
                val updatedSession = session.copy(
                    remainingAutonomousActions = (session.remainingAutonomousActions - 1).coerceAtLeast(0)
                )
                sessionStore.saveSession(updatedSession)
                auditLedger.record(
                    "operator.autonomy_budget_consumed",
                    "${request.id}:${request.packageName}:${updatedSession.remainingAutonomousActions}"
                )
            }
        }
        return liveResult
    }

    fun whitelistedPackages(): Set<String> = registryProvider().whitelistedPackages()

    fun supportsActionType(actionType: String): Boolean =
        registryProvider().forAction(actionType) != null

    fun queuedOperations(): List<AppOperationQueueItem> = operationQueue.pending()

    private fun blockedRoute(
        actionType: String,
        reason: String,
        playbook: AppOperationPlaybook? = null
    ): AppAgentRoute {
        auditLedger.record("agent_route.blocked", "$actionType $reason")
        return AppAgentRoute(
            status = AppAgentRouteStatus.BLOCKED,
            actionType = actionType,
            playbook = playbook,
            reason = reason
        )
    }

    private fun surfaceFor(playbook: AppOperationPlaybook, session: AppOperationSession?): AppAgentSurface =
        AppAgentSurface(
            id = "${playbook.packageName}:${playbook.actionType}",
            appName = appNameFor(playbook.packageName),
            packageName = playbook.packageName,
            loginState = session?.loginState ?: AppLoginState.LOGGED_OUT,
            autonomyMode = session?.autonomyMode ?: AutonomyMode.ASK_ONLY,
            allowedPlaybookIds = if (session?.revoked == true) emptySet() else session?.allowedPlaybookIds ?: emptySet(),
            supportedActionTypes = setOf(playbook.actionType),
            allowedSourceIds = if (session?.revoked == true) emptySet() else session?.allowedSourceIds ?: emptySet()
        )

    private fun appNameFor(packageName: String): String =
        when (packageName) {
            "com.google.android.apps.messaging" -> "Messages"
            "com.google.android.calendar" -> "Calendar"
            "com.google.android.apps.maps" -> "Maps"
            "com.facebook.katana" -> "Facebook"
            "com.example.notes" -> "Notes"
            "com.example.community" -> "Community"
            else -> packageName
        }

    private fun hasSignedInUser(userId: String): Boolean =
        userId.isNotBlank() && userId != "signed_out"

    private fun resultForRouteHandoff(
        route: AppAgentRoute,
        input: Map<String, String>,
        approvalReceipt: AppOperationApprovalReceipt?,
        userId: String
    ): AppOperationResult {
        val playbook = route.playbook
        val surface = route.surface
        val request = AppOperationRequest(
            id = "operation_route_${route.actionType.replace(".", "_")}",
            userId = userId,
            packageName = surface?.packageName ?: "unknown",
            playbookId = playbook?.id ?: "unknown",
            approvalReceipt = approvalReceipt,
            requiredSourceIds = route.missingSourceIds,
            input = input
        )

        if (route.status == AppAgentRouteStatus.BLOCKED) {
            return blocked(request, route.reason ?: "Route blocked")
        }

        val action = when (route.status) {
            AppAgentRouteStatus.NEEDS_LOGIN -> if (route.reason == "session_expired") {
                "Open ${surface?.appName ?: surface?.packageName ?: "the app"}, sign in, and renew this app-agent grant."
            } else {
                "Open ${surface?.appName ?: surface?.packageName ?: "the app"}, sign in, and return to Conductor."
            }
            AppAgentRouteStatus.NEEDS_GRANT -> "Grant ${surface?.appName ?: surface?.packageName ?: "the app"} access to ${playbook?.id ?: route.actionType}."
            else -> "Confirm this app operation before execution."
        }
        val missing = route.missingSourceIds.takeIf { it.isNotEmpty() }?.joinToString(prefix = " Missing sources: ")
            .orEmpty()

        val primaryActionLabel = when (route.status) {
            AppAgentRouteStatus.NEEDS_LOGIN -> if (route.reason == "session_expired") "Renew session" else "Confirm login"
            AppAgentRouteStatus.NEEDS_GRANT -> if (route.reason == "app_agent_source_grant_required") "Restore data access" else "Grant access"
            else -> "Review handoff"
        }

        return queueForUserAction(
            request = request,
            reason = route.reason ?: "App agent route requires user handoff",
            requiredUserAction = action + missing,
            primaryActionLabel = primaryActionLabel
        )
    }

    private fun blocked(request: AppOperationRequest, reason: String): AppOperationResult {
        auditLedger.record("operator.blocked", "${request.id} $reason")
        return AppOperationResult(
            requestId = request.id,
            status = AppOperationStatus.BLOCKED,
            detail = reason
        )
    }

    private fun queueForUserAction(
        request: AppOperationRequest,
        reason: String,
        requiredUserAction: String,
        primaryActionLabel: String = "Grant and retry",
        autonomyContext: String = ""
    ): AppOperationResult {
        val createdAtIso = nowIso()
        operationQueue.enqueue(
            AppOperationQueueItem(
                request = request,
                reason = reason,
                requiredUserAction = requiredUserAction,
                primaryActionLabel = primaryActionLabel,
                autonomyContext = autonomyContext,
                createdAtIso = createdAtIso,
                expiresAtIso = handoffExpiresAtIso(createdAtIso)
            )
        )
        auditLedger.record("operator.queued", "${request.id} $reason")
        if (autonomyContext.isNotBlank()) {
            auditLedger.record("operator.autonomy_handoff", "${request.id} $autonomyContext")
        }
        auditLedger.record("operator.needs_handoff", "${request.id} $requiredUserAction")
        return AppOperationResult(
            requestId = request.id,
            status = AppOperationStatus.NEEDS_HANDOFF,
            detail = "$reason. $requiredUserAction"
        )
    }

    private fun requiredInputsFor(playbook: AppOperationPlaybook): Set<String> =
        playbook.requiredInputKeys.ifEmpty {
            when (playbook.actionType) {
                "outbound_message.create_draft" -> setOf("recipient", "body")
                "outbound_message.send" -> setOf("recipient", "exactBody")
                "public_post.create" -> setOf("exactBody")
                "calendar.hold.create" -> setOf("title", "startsAtIso")
                "maps.route.open" -> setOf("destination")
                else -> emptySet()
            }
        }

    private fun exactApprovalContent(request: AppOperationRequest): String =
        request.input["exactBody"]
            ?: request.input["body"]
            ?: request.input["title"]
            ?: request.input.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }

    private fun handoffExpiresAtIso(createdAtIso: String): String =
        try {
            SystemClock.plusMinutes(30, createdAtIso)
        } catch (_: Exception) {
            if (createdAtIso == SystemClock.DEMO_NOW_ISO) {
                SystemClock.DEMO_HANDOFF_EXPIRES_ISO
            } else {
                createdAtIso
            }
        }

    private fun executionPreview(
        request: AppOperationRequest,
        playbook: AppOperationPlaybook,
        session: AppOperationSession,
        autonomyDecision: AppOperationAutonomyDecision,
        exactApprovalRequired: Boolean
    ): String =
        listOf(
            request.id,
            "app=${request.packageName}",
            "playbook=${playbook.id}",
            "action=${playbook.actionType}",
            "accountProof=${session.loginProof.method}:${session.loginProof.subjectLabel}",
            "sources=${request.requiredSourceIds.toList().sorted().joinToString()}",
            "inputs=${request.input.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }}",
            "steps=${playbook.steps.joinToString("|") { it.previewSummary() }}",
            "exactApproval=$exactApprovalRequired:${request.approvalReceipt?.approvalId.orEmpty()}",
            "autonomy=${session.autonomyMode}:${autonomyDecision.auditDetail}:sessionApprovalOverride=${session.requiresApprovalFor(playbook.actionType)}:budget=${session.remainingAutonomousActions}"
        ).joinToString(" ")

    private fun AppOperationStep.previewSummary(): String =
        listOfNotNull(
            operation.takeIf { it.isNotBlank() },
            selectorHint.takeIf { it.isNotBlank() }?.let { "target=$it" },
            inputKey.takeIf { it.isNotBlank() }?.let { "input=$it" },
            expectedState.takeIf { it.isNotBlank() }?.let { "verify=$it" },
            recoverySelectorHints.takeIf { it.isNotEmpty() }?.let { "recover=${it.joinToString("|")}" }
        ).joinToString(":")

    private fun evaluateSessionAutonomy(
        session: AppOperationSession,
        playbook: AppOperationPlaybook,
        exactApprovalRequired: Boolean
    ): AppOperationAutonomyDecision =
        when (session.autonomyMode) {
            AutonomyMode.ASK_ONLY -> AppOperationAutonomyDecision(
                allowed = false,
                reason = "App session autonomy ASK_ONLY prevents direct app operation.",
                auditDetail = "${session.autonomyMode}:${playbook.riskLabel}:blocked"
            )
            AutonomyMode.DRAFT_ONLY -> AppOperationAutonomyDecision(
                allowed = exactApprovalRequired || playbook.riskLabel.startsWith("low_"),
                reason = "DRAFT_ONLY app session requires exact approval or a low-risk reversible operation.",
                auditDetail = "${session.autonomyMode}:${playbook.riskLabel}:exactApproval=$exactApprovalRequired"
            )
            AutonomyMode.LOW_RISK_AUTO -> AppOperationAutonomyDecision(
                allowed = playbook.riskLabel.startsWith("low_") || exactApprovalRequired,
                reason = "LOW_RISK_AUTO app session requires low risk or exact user approval.",
                auditDetail = "${session.autonomyMode}:${playbook.riskLabel}:exactApproval=$exactApprovalRequired"
            )
            AutonomyMode.TRUSTED_AUTO -> AppOperationAutonomyDecision(
                allowed = true,
                reason = "Trusted app session allows this whitelisted playbook.",
                auditDetail = "${session.autonomyMode}:${playbook.riskLabel}:trusted"
            )
        }
}

private data class AppOperationAutonomyDecision(
    val allowed: Boolean,
    val reason: String,
    val auditDetail: String
)

interface AppOperationSourceAuthorizer {
    fun unauthorizedSourceIds(packageName: String, requiredSourceIds: Set<String>): Set<String>
}

class AllowAllAppOperationSourceAuthorizer : AppOperationSourceAuthorizer {
    override fun unauthorizedSourceIds(packageName: String, requiredSourceIds: Set<String>): Set<String> = emptySet()
}

class RecordBackedAppOperationSourceAuthorizer(
    private val recordStore: ConductorRecordStore,
    private val appAgentId: String = "conductor.voice",
    private val purpose: String = "activity_planning",
    private val nowIso: () -> String = { SystemClock.nowIso() }
) : AppOperationSourceAuthorizer {
    override fun unauthorizedSourceIds(packageName: String, requiredSourceIds: Set<String>): Set<String> {
        if (requiredSourceIds.isEmpty()) return emptySet()
        val activeBaseSources = recordStore.graphGrants()
            .filter { grant ->
                !grant.revoked &&
                    !grant.isExpired(nowIso()) &&
                    grant.purposes.contains(purpose)
            }
            .map { it.source }
            .toSet()
        val activeSources = recordStore.appAgentGrants()
            .filter { grant ->
                !grant.revoked &&
                    !grant.isExpired(nowIso()) &&
                    grant.appAgentId == appAgentId &&
                    grant.packageName == packageName &&
                    grant.purposes.contains(purpose)
            }
            .flatMap { it.sources }
            .toSet()
        return requiredSourceIds
            .filter { !activeBaseSources.contains(it) || !activeSources.contains(it) }
            .toSet()
    }
}
