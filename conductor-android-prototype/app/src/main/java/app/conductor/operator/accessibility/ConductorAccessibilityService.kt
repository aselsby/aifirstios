package app.conductor.operator.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.conductor.audit.AuditLedger
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.SystemClock
import app.conductor.storage.AndroidConductorRecordStoreFactory

class ConductorAccessibilityService : AccessibilityService() {
    private var activePackageName: String? = null
    private val recordStore by lazy { AndroidConductorRecordStoreFactory.create(applicationContext) }
    private val playbookRegistryProvider: () -> AppOperationPlaybookRegistry = {
        AppOperationPlaybookRegistry(
            customPlaybooks = recordStore.appOperationPlaybooks()
        )
    }
    private val auditLedger by lazy { AuditLedger(recordStore) }
    private val approvalReceiptLedger by lazy {
        RecordBackedAppOperationApprovalReceiptLedger(recordStore)
    }
    private val sessionStore by lazy {
        RecordBackedAppOperationSessionStore(recordStore)
    }
    private val liveBridge by lazy {
        AccessibilityAppOperationLiveBridge(
            auditLedger = auditLedger,
            activeRootProvider = { resolveActiveRoot() },
            activePackageProvider = { activePackageName },
            foregroundLauncher = AndroidAppForegroundLauncher(applicationContext, auditLedger),
            cancellationRequested = { recordStore.autonomyMode() == AutonomyMode.ASK_ONLY }
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i("ConductorOS", "accessibility.service_connected")
        // Drain any queued live work when the service binds.
        drainQueuedOperations(activePackageName ?: applicationContext.packageName)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        activePackageName = packageName
        val root = resolveActiveRoot()
        if (packageName != applicationContext.packageName && root != null) {
            val discovery = root.toAppAgentDiscovery(packageName)
            if (discovery.visibleLabelCounts.isNotEmpty()) {
                recordStore.saveAppAgentDiscovery(discovery)
                auditLedger.record(
                    "app_agent.discovery_observed",
                    "$packageName:${discovery.visibleLabelCounts.size}:${discovery.accountProofCandidates.size}"
                )
            }
        }
        val whitelistedPackages = playbookRegistryProvider().whitelistedPackages()
        if (!whitelistedPackages.contains(packageName)) {
            return
        }
        if (recordStore.autonomyMode() == AutonomyMode.ASK_ONLY) {
            auditLedger.record("accessibility.autonomy_stop_observed", packageName)
            return
        }

        // Drain on window transitions; content-change floods ANR if we re-launch every event.
        val eventType = event.eventType
        val shouldDrain =
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        if (!shouldDrain) return

        // Throttle content-change drains.
        val now = System.currentTimeMillis()
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            now - lastDrainAtMs < 400
        ) {
            return
        }
        lastDrainAtMs = now
        auditLedger.record("accessibility.window_observed", packageName)
        drainQueuedOperations(packageName)
    }

    private var lastDrainAtMs: Long = 0

    private fun drainQueuedOperations(packageName: String) {
        val now = SystemClock.nowIso()
        recordStore.queuedAppOperations()
            .filterNot { queued ->
                queued.isExpired(now).also { expired ->
                    if (expired) {
                        recordStore.resolveQueuedAppOperation(queued.request.id)
                        auditLedger.record("accessibility.queue_expired", queued.request.id)
                    }
                }
            }
            .filter { it.request.packageName == packageName }
            .filter { it.primaryActionLabel == "Run in app" || it.primaryActionLabel == "Apply autonomy and retry" }
            .forEach { queued ->
                val playbook = playbookRegistryProvider().find(queued.request.playbookId) ?: return@forEach
                val result = liveBridge.dispatch(queued.request, playbook)
                if (result.status == AppOperationStatus.VERIFIED) {
                    finalizeVerifiedOperation(queued, playbook)
                    recordStore.resolveQueuedAppOperation(queued.request.id)
                    auditLedger.record("accessibility.queue_resolved", queued.request.id)
                    Log.i("ConductorOS", "accessibility.queue_resolved ${queued.request.id}:${playbook.id}")
                } else {
                    auditLedger.record("accessibility.queue_still_pending", "${queued.request.id}:${result.detail}")
                    Log.i("ConductorOS", "accessibility.queue_still_pending ${queued.request.id}:${result.detail}")
                }
            }
    }

    private fun resolveActiveRoot(): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { return it }
        return try {
            windows
                ?.firstOrNull { window -> window.isActive || window.isFocused }
                ?.root
        } catch (_: Exception) {
            null
        }
    }

    override fun onInterrupt() {
        recordStore.saveAutonomyMode(AutonomyMode.ASK_ONLY)
        recordStore.clearQueuedAppOperations()
        auditLedger.record("accessibility.interrupted", "Live app operation interrupted by the system.")
    }

    private fun finalizeVerifiedOperation(queued: AppOperationQueueItem, playbook: AppOperationPlaybook) {
        val request = queued.request
        if (request.requiredSourceIds.isNotEmpty()) {
            auditLedger.record(
                "operator.source_scope_verified",
                "${request.id} ${request.packageName}:${request.requiredSourceIds.toList().sorted().joinToString()}"
            )
        }
        request.approvalReceipt?.let { receipt ->
            if (!approvalReceiptLedger.isConsumed(receipt.approvalId)) {
                approvalReceiptLedger.consume(receipt)
                auditLedger.record("operator.approval_consumed", "${request.id}:${receipt.approvalId}")
            }
        }
        val exactApprovalRequired = playbook.requiresExactApproval ||
            sessionStore.sessionFor(request.userId, request.packageName)
                ?.requiresApprovalFor(playbook.actionType) == true
        if (!exactApprovalRequired) {
            val session = sessionStore.sessionFor(request.userId, request.packageName) ?: return
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

    private fun AccessibilityNodeInfo.toAppAgentDiscovery(packageName: String): AppAgentDiscovery {
        val labels = linkedMapOf<String, Int>()
        collectVisibleLabels(labels)
        val boundedLabels = labels.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(MAX_DISCOVERY_LABELS)
            .associate { it.key to it.value }
        val accountCandidates = boundedLabels.keys
            .filter { label ->
                label.contains("@") ||
                    label.contains("signed in", ignoreCase = true) ||
                    label.contains("account", ignoreCase = true)
            }
            .take(MAX_ACCOUNT_PROOF_CANDIDATES)
            .toSet()
        return AppAgentDiscovery(
            packageName = packageName,
            observedAtIso = SystemClock.nowIso(),
            visibleLabelCounts = boundedLabels,
            accountProofCandidates = accountCandidates,
            bounded = labels.size > boundedLabels.size
        )
    }

    private fun AccessibilityNodeInfo.collectVisibleLabels(labels: MutableMap<String, Int>) {
        if (!isVisibleToUser) return
        listOfNotNull(safeNodeLabel(text), safeNodeLabel(contentDescription)).forEach { label ->
            labels[label] = (labels[label] ?: 0) + 1
        }
        for (index in 0 until childCount) {
            getChild(index)?.collectVisibleLabels(labels)
        }
    }

    private fun AccessibilityNodeInfo.safeNodeLabel(value: CharSequence?): String? {
        if (isPassword || className?.contains("EditText", ignoreCase = true) == true) return null
        val normalized = value
            ?.toString()
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        return normalized
            .takeIf { it.length in 2..MAX_DISCOVERY_LABEL_LENGTH }
            ?.takeIf { !it.contains("password", ignoreCase = true) }
    }

    private companion object {
        const val MAX_DISCOVERY_LABELS = 40
        const val MAX_ACCOUNT_PROOF_CANDIDATES = 5
        const val MAX_DISCOVERY_LABEL_LENGTH = 48
    }
}
