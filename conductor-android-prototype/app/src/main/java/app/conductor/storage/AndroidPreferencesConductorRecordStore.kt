package app.conductor.storage

import android.content.SharedPreferences
import app.conductor.account.AccountSession
import app.conductor.connectors.ConnectedAccount
import app.conductor.graph.AppAgentGrant
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.operator.accessibility.AppLoginState
import app.conductor.operator.accessibility.AppLoginProof
import app.conductor.operator.accessibility.AppOperationApprovalReceipt
import app.conductor.operator.accessibility.AppAgentDiscovery
import app.conductor.operator.accessibility.AppOperationPlaybook
import app.conductor.operator.accessibility.AppOperationQueueItem
import app.conductor.operator.accessibility.AppOperationRequest
import app.conductor.operator.accessibility.AppOperationSession
import app.conductor.operator.accessibility.AppOperationStep
import app.conductor.runtime.ApprovalStatus
import app.conductor.runtime.AuditEvent
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.OperationTimeline
import app.conductor.runtime.OperationTimelineEvent
import app.conductor.runtime.Task
import org.json.JSONArray
import org.json.JSONObject

class AndroidPreferencesConductorRecordStore(
    private val preferences: SharedPreferences
) : ConductorRecordStore {
    override fun saveTask(task: Task) {
        upsert(KEY_TASKS, task.id, task.toJson())
    }

    override fun tasks(): List<Task> =
        readArray(KEY_TASKS).map { it.toTask() }

    override fun saveOperationTimeline(timeline: OperationTimeline) {
        upsert(KEY_OPERATION_TIMELINES, timeline.id, timeline.toJson())
    }

    override fun operationTimelines(): List<OperationTimeline> =
        readArray(KEY_OPERATION_TIMELINES).map { it.toOperationTimeline() }

    override fun saveGraphGrant(grant: GraphGrant) {
        upsert(KEY_GRAPH_GRANTS, grant.id, grant.toJson())
    }

    override fun graphGrants(): List<GraphGrant> =
        readArray(KEY_GRAPH_GRANTS).map { it.toGraphGrant() }

    override fun saveAppAgentGrant(grant: AppAgentGrant) {
        upsert(KEY_APP_AGENT_GRANTS, grant.id, grant.toJson())
    }

    override fun appAgentGrants(): List<AppAgentGrant> =
        readArray(KEY_APP_AGENT_GRANTS).map { it.toAppAgentGrant() }

    override fun saveGraphFact(fact: GraphFact) {
        upsert(KEY_GRAPH_FACTS, fact.id, fact.toJson())
    }

    override fun graphFacts(): List<GraphFact> =
        readArray(KEY_GRAPH_FACTS).map { it.toGraphFact() }

    override fun saveApprovalDecision(decision: StoredApprovalDecision) {
        upsert(KEY_APPROVAL_DECISIONS, decision.id, decision.toJson())
    }

    override fun approvalDecisions(): List<StoredApprovalDecision> =
        readArray(KEY_APPROVAL_DECISIONS).map { it.toApprovalDecision() }

    override fun saveConsumedApprovalReceipt(receipt: StoredConsumedApprovalReceipt) {
        upsert(KEY_CONSUMED_APPROVAL_RECEIPTS, receipt.approvalId, receipt.toJson())
    }

    override fun consumedApprovalReceipts(): List<StoredConsumedApprovalReceipt> =
        readArray(KEY_CONSUMED_APPROVAL_RECEIPTS).map { it.toConsumedApprovalReceipt() }

    override fun saveAutonomyMode(mode: AutonomyMode) {
        preferences.edit().putString(KEY_AUTONOMY_MODE, mode.name).apply()
    }

    override fun autonomyMode(): AutonomyMode? =
        preferences.getString(KEY_AUTONOMY_MODE, null)?.let(AutonomyMode::valueOf)

    override fun saveAccountSession(session: AccountSession) {
        upsert(KEY_ACCOUNT_SESSIONS, session.userId, session.toJson())
    }

    override fun accountSessions(): List<AccountSession> =
        readArray(KEY_ACCOUNT_SESSIONS).map { it.toAccountSession() }

    override fun saveConnectorAccount(account: ConnectedAccount) {
        upsert(KEY_CONNECTOR_ACCOUNTS, "${account.source}:${account.accountId}", account.toJson())
    }

    override fun connectorAccounts(): List<ConnectedAccount> =
        readArray(KEY_CONNECTOR_ACCOUNTS).map { it.toConnectedAccount() }

    override fun saveAppOperationSession(session: AppOperationSession) {
        upsert(KEY_APP_OPERATION_SESSIONS, "${session.userId}:${session.packageName}", session.toJson())
    }

    override fun appOperationSessions(): List<AppOperationSession> =
        readArray(KEY_APP_OPERATION_SESSIONS).map { it.toAppOperationSession() }

    override fun saveAppOperationPlaybook(playbook: AppOperationPlaybook) {
        upsert(KEY_APP_OPERATION_PLAYBOOKS, playbook.id, playbook.toJson())
    }

    override fun appOperationPlaybooks(): List<AppOperationPlaybook> =
        readArray(KEY_APP_OPERATION_PLAYBOOKS).map { it.toAppOperationPlaybook() }

    override fun saveAppAgentDiscovery(discovery: AppAgentDiscovery) {
        upsert(KEY_APP_AGENT_DISCOVERIES, discovery.packageName, discovery.toJson())
    }

    override fun appAgentDiscoveries(): List<AppAgentDiscovery> =
        readArray(KEY_APP_AGENT_DISCOVERIES).map { it.toAppAgentDiscovery() }

    override fun enqueueAppOperation(item: AppOperationQueueItem) {
        upsert(KEY_APP_OPERATION_QUEUE, item.request.id, item.toJson())
    }

    override fun queuedAppOperations(): List<AppOperationQueueItem> =
        readArray(KEY_APP_OPERATION_QUEUE).map { it.toAppOperationQueueItem() }

    override fun resolveQueuedAppOperation(requestId: String) {
        remove(KEY_APP_OPERATION_QUEUE, requestId)
    }

    override fun clearQueuedAppOperations() {
        preferences.edit().putString(KEY_APP_OPERATION_QUEUE, "[]").apply()
    }

    override fun appendAudit(event: AuditEvent) {
        append(KEY_AUDIT_EVENTS, event.toJson())
    }

    override fun auditEvents(): List<AuditEvent> =
        readArray(KEY_AUDIT_EVENTS).map { it.toAuditEvent() }

    private fun upsert(key: String, id: String, item: JSONObject) {
        item.put("_record_id", id)
        val existing = readArray(key).filter { it.optString("_record_id") != id }
        writeArray(key, existing + item)
    }

    private fun append(key: String, item: JSONObject) {
        writeArray(key, readArray(key) + item)
    }

    private fun remove(key: String, id: String) {
        writeArray(key, readArray(key).filter { it.optString("_record_id") != id })
    }

    private fun readArray(key: String): List<JSONObject> {
        val raw = preferences.getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { index -> array.getJSONObject(index) }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val array = JSONArray()
        items.forEach(array::put)
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun Set<String>.toJsonArray(): JSONArray =
        JSONArray().also { array -> sorted().forEach(array::put) }

    private fun Map<String, String>.toJsonObject(): JSONObject =
        JSONObject().also { obj -> entries.sortedBy { it.key }.forEach { obj.put(it.key, it.value) } }

    private fun Map<String, Int>.toIntJsonObject(): JSONObject =
        JSONObject().also { obj -> entries.sortedBy { it.key }.forEach { obj.put(it.key, it.value) } }

    private fun JSONArray.toStringSet(): Set<String> =
        (0 until length()).map { getString(it) }.toSet()

    private fun JSONObject.toStringMap(): Map<String, String> =
        keys().asSequence().associateWith { getString(it) }

    private fun JSONObject.toIntMap(): Map<String, Int> =
        keys().asSequence().associateWith { getInt(it) }

    private fun Task.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("userId", userId)
            .put("goal", goal)
            .put("intentType", intentType)
            .put("mode", mode.name)
            .put("createdAtIso", createdAtIso)

    private fun JSONObject.toTask(): Task =
        Task(
            id = getString("id"),
            userId = getString("userId"),
            goal = getString("goal"),
            intentType = optString("intentType", "outdoor_activity"),
            mode = AutonomyMode.valueOf(getString("mode")),
            createdAtIso = getString("createdAtIso")
        )

    private fun OperationTimeline.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("taskId", taskId)
            .put("userId", userId)
            .put("intentType", intentType)
            .put("autonomyMode", autonomyMode.name)
            .put("startedAtIso", startedAtIso)
            .put("updatedAtIso", updatedAtIso)
            .put("events", JSONArray().also { array -> events.forEach { array.put(it.toJson()) } })

    private fun JSONObject.toOperationTimeline(): OperationTimeline =
        OperationTimeline(
            id = getString("id"),
            taskId = getString("taskId"),
            userId = getString("userId"),
            intentType = getString("intentType"),
            autonomyMode = AutonomyMode.valueOf(getString("autonomyMode")),
            startedAtIso = getString("startedAtIso"),
            updatedAtIso = getString("updatedAtIso"),
            events = getJSONArray("events").let { array ->
                (0 until array.length()).map { array.getJSONObject(it).toOperationTimelineEvent() }
            }
        )

    private fun OperationTimelineEvent.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("stepId", stepId)
            .put("actionType", actionType)
            .put("tool", tool)
            .put("status", status)
            .put("policyDecision", policyDecision)
            .put("approvalId", approvalId)
            .put("packageName", packageName)
            .put("playbookId", playbookId)
            .put("sourceScope", sourceScope.toSet().toJsonArray())
            .put("summary", summary)

    private fun JSONObject.toOperationTimelineEvent(): OperationTimelineEvent =
        OperationTimelineEvent(
            id = getString("id"),
            stepId = getString("stepId"),
            actionType = getString("actionType"),
            tool = getString("tool"),
            status = getString("status"),
            policyDecision = getString("policyDecision"),
            approvalId = optString("approvalId", ""),
            packageName = optString("packageName", ""),
            playbookId = optString("playbookId", ""),
            sourceScope = optJSONArray("sourceScope")?.toStringSet()?.toList()?.sorted().orEmpty(),
            summary = getString("summary")
        )

    private fun GraphGrant.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("source", source)
            .put("accountId", accountId)
            .put("purposes", purposes.toJsonArray())
            .put("revoked", revoked)
            .put("expiresAtIso", expiresAtIso)

    private fun JSONObject.toGraphGrant(): GraphGrant =
        GraphGrant(
            id = getString("id"),
            source = getString("source"),
            accountId = getString("accountId"),
            purposes = getJSONArray("purposes").toStringSet(),
            revoked = getBoolean("revoked"),
            expiresAtIso = nullableString("expiresAtIso")
        )

    private fun AppAgentGrant.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("appAgentId", appAgentId)
            .put("packageName", packageName)
            .put("purposes", purposes.toJsonArray())
            .put("sources", sources.toJsonArray())
            .put("revoked", revoked)
            .put("expiresAtIso", expiresAtIso)

    private fun JSONObject.toAppAgentGrant(): AppAgentGrant =
        AppAgentGrant(
            id = getString("id"),
            appAgentId = getString("appAgentId"),
            packageName = getString("packageName"),
            purposes = getJSONArray("purposes").toStringSet(),
            sources = getJSONArray("sources").toStringSet(),
            revoked = getBoolean("revoked"),
            expiresAtIso = nullableString("expiresAtIso")
        )

    private fun GraphFact.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("type", type)
            .put("source", source)
            .put("accountId", accountId)
            .put("summary", summary)
            .put("redactedSummary", redactedSummary)
            .put("sensitivity", sensitivity.name)
            .put("allowedPurposes", allowedPurposes.toJsonArray())
            .put("expiresAtIso", expiresAtIso)

    private fun JSONObject.toGraphFact(): GraphFact =
        GraphFact(
            id = getString("id"),
            type = getString("type"),
            source = getString("source"),
            accountId = getString("accountId"),
            summary = getString("summary"),
            redactedSummary = nullableString("redactedSummary"),
            sensitivity = Sensitivity.valueOf(getString("sensitivity")),
            allowedPurposes = getJSONArray("allowedPurposes").toStringSet(),
            expiresAtIso = nullableString("expiresAtIso")
        )

    private fun StoredApprovalDecision.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("status", status.name)
            .put("decidedAtIso", decidedAtIso)
            .put("actionType", actionType)
            .put("exactContent", exactContent)

    private fun JSONObject.toApprovalDecision(): StoredApprovalDecision =
        StoredApprovalDecision(
            id = getString("id"),
            status = ApprovalStatus.valueOf(getString("status")),
            decidedAtIso = getString("decidedAtIso"),
            actionType = nullableString("actionType"),
            exactContent = nullableString("exactContent")
        )

    private fun StoredConsumedApprovalReceipt.toJson(): JSONObject =
        JSONObject()
            .put("approvalId", approvalId)
            .put("actionType", actionType)
            .put("consumedAtIso", consumedAtIso)

    private fun JSONObject.toConsumedApprovalReceipt(): StoredConsumedApprovalReceipt =
        StoredConsumedApprovalReceipt(
            approvalId = getString("approvalId"),
            actionType = getString("actionType"),
            consumedAtIso = getString("consumedAtIso")
        )

    private fun AccountSession.toJson(): JSONObject =
        JSONObject()
            .put("userId", userId)
            .put("displayName", displayName)
            .put("bearerToken", bearerToken)
            .put("loggedIn", loggedIn)
            .put("expiresAtIso", expiresAtIso)

    private fun JSONObject.toAccountSession(): AccountSession =
        AccountSession(
            userId = getString("userId"),
            displayName = getString("displayName"),
            bearerToken = getString("bearerToken"),
            loggedIn = getBoolean("loggedIn"),
            expiresAtIso = getString("expiresAtIso")
        )

    private fun ConnectedAccount.toJson(): JSONObject =
        JSONObject()
            .put("source", source)
            .put("accountId", accountId)
            .put("credentialHandle", credentialHandle)
            .put("purposes", purposes.toJsonArray())

    private fun JSONObject.toConnectedAccount(): ConnectedAccount =
        ConnectedAccount(
            source = getString("source"),
            accountId = getString("accountId"),
            credentialHandle = getString("credentialHandle"),
            purposes = getJSONArray("purposes").toStringSet()
        )

    private fun AppOperationSession.toJson(): JSONObject =
        JSONObject()
            .put("userId", userId)
            .put("packageName", packageName)
            .put("loginState", loginState.name)
            .put("autonomyMode", autonomyMode.name)
            .put("allowedPlaybookIds", allowedPlaybookIds.toJsonArray())
            .put("allowedSourceIds", allowedSourceIds.toJsonArray())
            .put("approvalRequiredActionTypes", approvalRequiredActionTypes.toJsonArray())
            .put("remainingAutonomousActions", remainingAutonomousActions)
            .put("loginProof", loginProof.toJson())
            .put("revoked", revoked)
            .put("expiresAtIso", expiresAtIso)

    private fun JSONObject.toAppOperationSession(): AppOperationSession =
        AppOperationSession(
            userId = getString("userId"),
            packageName = getString("packageName"),
            loginState = AppLoginState.valueOf(getString("loginState")),
            autonomyMode = AutonomyMode.valueOf(getString("autonomyMode")),
            allowedPlaybookIds = getJSONArray("allowedPlaybookIds").toStringSet(),
            allowedSourceIds = getJSONArray("allowedSourceIds").toStringSet(),
            approvalRequiredActionTypes = optJSONArray("approvalRequiredActionTypes")?.toStringSet() ?: emptySet(),
            remainingAutonomousActions = optInt("remainingAutonomousActions", 3),
            loginProof = getJSONObject("loginProof").toAppLoginProof(),
            revoked = getBoolean("revoked"),
            expiresAtIso = getString("expiresAtIso")
        )

    private fun AppLoginProof.toJson(): JSONObject =
        JSONObject()
            .put("method", method)
            .put("subjectLabel", subjectLabel)
            .put("verifiedAtIso", verifiedAtIso)

    private fun JSONObject.toAppLoginProof(): AppLoginProof =
        AppLoginProof(
            method = getString("method"),
            subjectLabel = getString("subjectLabel"),
            verifiedAtIso = getString("verifiedAtIso")
        )

    private fun AppOperationPlaybook.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("packageName", packageName)
            .put("actionType", actionType)
            .put("riskLabel", riskLabel)
            .put("requiresExactApproval", requiresExactApproval)
            .put("invocationPhrases", invocationPhrases.toJsonArray())
            .put("accountProofLabel", accountProofLabel)
            .put("requiredInputKeys", requiredInputKeys.toJsonArray())
            .put("requiredSourceIds", requiredSourceIds.toJsonArray())
            .put("steps", JSONArray().also { array -> steps.forEach { array.put(it.toJson()) } })

    private fun JSONObject.toAppOperationPlaybook(): AppOperationPlaybook =
        AppOperationPlaybook(
            id = getString("id"),
            packageName = getString("packageName"),
            actionType = getString("actionType"),
            riskLabel = getString("riskLabel"),
            requiresExactApproval = getBoolean("requiresExactApproval"),
            invocationPhrases = optJSONArray("invocationPhrases")?.toStringSet() ?: emptySet(),
            accountProofLabel = optString("accountProofLabel", ""),
            requiredInputKeys = getJSONArray("requiredInputKeys").toStringSet(),
            requiredSourceIds = optJSONArray("requiredSourceIds")?.toStringSet() ?: emptySet(),
            steps = getJSONArray("steps").let { array ->
                (0 until array.length()).map { array.getJSONObject(it).toAppOperationStep() }
            }
        )

    private fun AppAgentDiscovery.toJson(): JSONObject =
        JSONObject()
            .put("packageName", packageName)
            .put("observedAtIso", observedAtIso)
            .put("visibleLabelCounts", visibleLabelCounts.toIntJsonObject())
            .put("accountProofCandidates", accountProofCandidates.toJsonArray())
            .put("bounded", bounded)

    private fun JSONObject.toAppAgentDiscovery(): AppAgentDiscovery =
        AppAgentDiscovery(
            packageName = getString("packageName"),
            observedAtIso = getString("observedAtIso"),
            visibleLabelCounts = getJSONObject("visibleLabelCounts").toIntMap(),
            accountProofCandidates = getJSONArray("accountProofCandidates").toStringSet(),
            bounded = getBoolean("bounded")
        )

    private fun AppOperationStep.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("description", description)
            .put("selectorHint", selectorHint)
            .put("expectedState", expectedState)
            .put("operation", operation)
            .put("inputKey", inputKey)
            .put("recoverySelectorHints", recoverySelectorHints.toSet().toJsonArray())

    private fun JSONObject.toAppOperationStep(): AppOperationStep =
        AppOperationStep(
            id = getString("id"),
            description = getString("description"),
            selectorHint = getString("selectorHint"),
            expectedState = getString("expectedState"),
            operation = optString("operation", "auto"),
            inputKey = optString("inputKey", ""),
            recoverySelectorHints = optJSONArray("recoverySelectorHints")?.toStringSet()?.toList()?.sorted().orEmpty()
        )

    private fun AppOperationQueueItem.toJson(): JSONObject =
        JSONObject()
            .put("request", request.toJson())
            .put("reason", reason)
            .put("requiredUserAction", requiredUserAction)
            .put("primaryActionLabel", primaryActionLabel)
            .put("autonomyContext", autonomyContext)
            .put("createdAtIso", createdAtIso)
            .put("expiresAtIso", expiresAtIso)

    private fun JSONObject.toAppOperationQueueItem(): AppOperationQueueItem =
        AppOperationQueueItem(
            request = getJSONObject("request").toAppOperationRequest(),
            reason = getString("reason"),
            requiredUserAction = getString("requiredUserAction"),
            primaryActionLabel = optString("primaryActionLabel", "Grant and retry"),
            autonomyContext = optString("autonomyContext", ""),
            createdAtIso = optString("createdAtIso", "2026-07-27T10:45:00-05:00"),
            expiresAtIso = optString("expiresAtIso", "2026-07-27T11:15:00-05:00")
        )

    private fun AppOperationRequest.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("userId", userId)
            .put("packageName", packageName)
            .put("playbookId", playbookId)
            .put("approvalReceipt", approvalReceipt?.toJson())
            .put("requiredSourceIds", requiredSourceIds.toJsonArray())
            .put("input", input.toJsonObject())

    private fun JSONObject.toAppOperationRequest(): AppOperationRequest =
        AppOperationRequest(
            id = getString("id"),
            userId = getString("userId"),
            packageName = getString("packageName"),
            playbookId = getString("playbookId"),
            approvalReceipt = if (isNull("approvalReceipt")) null else getJSONObject("approvalReceipt").toApprovalReceipt(),
            requiredSourceIds = getJSONArray("requiredSourceIds").toStringSet(),
            input = getJSONObject("input").toStringMap()
        )

    private fun AppOperationApprovalReceipt.toJson(): JSONObject =
        JSONObject()
            .put("approvalId", approvalId)
            .put("actionType", actionType)
            .put("approvedExactContent", approvedExactContent)

    private fun JSONObject.toApprovalReceipt(): AppOperationApprovalReceipt =
        AppOperationApprovalReceipt(
            approvalId = getString("approvalId"),
            actionType = getString("actionType"),
            approvedExactContent = getString("approvedExactContent")
        )

    private fun AuditEvent.toJson(): JSONObject =
        JSONObject()
            .put("type", type)
            .put("detail", detail)

    private fun JSONObject.toAuditEvent(): AuditEvent =
        AuditEvent(type = getString("type"), detail = getString("detail"))

    private companion object {
        const val KEY_TASKS = "tasks"
        const val KEY_OPERATION_TIMELINES = "operation_timelines"
        const val KEY_GRAPH_GRANTS = "graph_grants"
        const val KEY_APP_AGENT_GRANTS = "app_agent_grants"
        const val KEY_GRAPH_FACTS = "graph_facts"
        const val KEY_APPROVAL_DECISIONS = "approval_decisions"
        const val KEY_CONSUMED_APPROVAL_RECEIPTS = "consumed_approval_receipts"
        const val KEY_AUTONOMY_MODE = "autonomy_mode"
        const val KEY_ACCOUNT_SESSIONS = "account_sessions"
        const val KEY_CONNECTOR_ACCOUNTS = "connector_accounts"
        const val KEY_APP_OPERATION_SESSIONS = "app_operation_sessions"
        const val KEY_APP_OPERATION_PLAYBOOKS = "app_operation_playbooks"
        const val KEY_APP_AGENT_DISCOVERIES = "app_agent_discoveries"
        const val KEY_APP_OPERATION_QUEUE = "app_operation_queue"
        const val KEY_AUDIT_EVENTS = "audit_events"
    }
}
