package app.conductor.storage

import app.conductor.account.AccountSession
import app.conductor.connectors.ConnectedAccount
import app.conductor.graph.AppAgentGrant
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.operator.accessibility.AppOperationPlaybook
import app.conductor.operator.accessibility.AppAgentDiscovery
import app.conductor.operator.accessibility.AppOperationQueueItem
import app.conductor.operator.accessibility.AppOperationSession
import app.conductor.runtime.ApprovalStatus
import app.conductor.runtime.AuditEvent
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.OperationTimeline
import app.conductor.runtime.Task

class InMemoryConductorRecordStore : ConductorRecordStore {
    private val taskRecords = linkedMapOf<String, Task>()
    private val operationTimelineRecords = linkedMapOf<String, OperationTimeline>()
    private val grantRecords = linkedMapOf<String, GraphGrant>()
    private val appAgentGrantRecords = linkedMapOf<String, AppAgentGrant>()
    private val factRecords = linkedMapOf<String, GraphFact>()
    private val approvalRecords = linkedMapOf<String, StoredApprovalDecision>()
    private val consumedApprovalReceiptRecords = linkedMapOf<String, StoredConsumedApprovalReceipt>()
    private val accountSessionRecords = linkedMapOf<String, AccountSession>()
    private val connectorAccountRecords = linkedMapOf<String, ConnectedAccount>()
    private val appOperationSessionRecords = linkedMapOf<String, AppOperationSession>()
    private val appOperationPlaybookRecords = linkedMapOf<String, AppOperationPlaybook>()
    private val appAgentDiscoveryRecords = linkedMapOf<String, AppAgentDiscovery>()
    private val appOperationQueueRecords = mutableListOf<AppOperationQueueItem>()
    private val auditRecords = mutableListOf<AuditEvent>()
    private var storedAutonomyMode: AutonomyMode? = null

    override fun saveTask(task: Task) {
        taskRecords[task.id] = task
    }

    override fun tasks(): List<Task> =
        taskRecords.values.toList()

    override fun saveOperationTimeline(timeline: OperationTimeline) {
        operationTimelineRecords[timeline.id] = timeline
    }

    override fun operationTimelines(): List<OperationTimeline> =
        operationTimelineRecords.values.toList()

    override fun saveGraphGrant(grant: GraphGrant) {
        grantRecords[grant.id] = grant
    }

    override fun graphGrants(): List<GraphGrant> =
        grantRecords.values.toList()

    override fun saveAppAgentGrant(grant: AppAgentGrant) {
        appAgentGrantRecords[grant.id] = grant
    }

    override fun appAgentGrants(): List<AppAgentGrant> =
        appAgentGrantRecords.values.toList()

    override fun saveGraphFact(fact: GraphFact) {
        factRecords[fact.id] = fact
    }

    override fun graphFacts(): List<GraphFact> =
        factRecords.values.toList()

    override fun saveApprovalDecision(decision: StoredApprovalDecision) {
        approvalRecords[decision.id] = decision
    }

    override fun approvalDecisions(): List<StoredApprovalDecision> =
        approvalRecords.values.toList()

    override fun saveConsumedApprovalReceipt(receipt: StoredConsumedApprovalReceipt) {
        consumedApprovalReceiptRecords[receipt.approvalId] = receipt
    }

    override fun consumedApprovalReceipts(): List<StoredConsumedApprovalReceipt> =
        consumedApprovalReceiptRecords.values.toList()

    override fun saveAutonomyMode(mode: AutonomyMode) {
        storedAutonomyMode = mode
    }

    override fun autonomyMode(): AutonomyMode? =
        storedAutonomyMode

    override fun saveAccountSession(session: AccountSession) {
        accountSessionRecords[session.userId] = session
    }

    override fun accountSessions(): List<AccountSession> =
        accountSessionRecords.values.toList()

    override fun saveConnectorAccount(account: ConnectedAccount) {
        connectorAccountRecords["${account.source}:${account.accountId}"] = account
    }

    override fun connectorAccounts(): List<ConnectedAccount> =
        connectorAccountRecords.values.toList()

    override fun saveAppOperationSession(session: AppOperationSession) {
        appOperationSessionRecords["${session.userId}:${session.packageName}"] = session
    }

    override fun appOperationSessions(): List<AppOperationSession> =
        appOperationSessionRecords.values.toList()

    override fun saveAppOperationPlaybook(playbook: AppOperationPlaybook) {
        appOperationPlaybookRecords[playbook.id] = playbook
    }

    override fun appOperationPlaybooks(): List<AppOperationPlaybook> =
        appOperationPlaybookRecords.values.toList()

    override fun saveAppAgentDiscovery(discovery: AppAgentDiscovery) {
        appAgentDiscoveryRecords[discovery.packageName] = discovery
    }

    override fun appAgentDiscoveries(): List<AppAgentDiscovery> =
        appAgentDiscoveryRecords.values.toList()

    override fun enqueueAppOperation(item: AppOperationQueueItem) {
        appOperationQueueRecords.removeAll { it.request.id == item.request.id }
        appOperationQueueRecords.add(item)
    }

    override fun queuedAppOperations(): List<AppOperationQueueItem> =
        appOperationQueueRecords.toList()

    override fun resolveQueuedAppOperation(requestId: String) {
        appOperationQueueRecords.removeAll { it.request.id == requestId }
    }

    override fun clearQueuedAppOperations() {
        appOperationQueueRecords.clear()
    }

    override fun appendAudit(event: AuditEvent) {
        auditRecords.add(event)
    }

    override fun auditEvents(): List<AuditEvent> =
        auditRecords.toList()

    fun approvedIds(): Set<String> =
        approvalRecords.values
            .filter { it.status == ApprovalStatus.APPROVED }
            .map { it.id }
            .toSet()

    fun deniedIds(): Set<String> =
        approvalRecords.values
            .filter { it.status == ApprovalStatus.DENIED }
            .map { it.id }
            .toSet()
}
