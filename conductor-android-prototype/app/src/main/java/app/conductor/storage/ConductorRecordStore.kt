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

data class StoredApprovalDecision(
    val id: String,
    val status: ApprovalStatus,
    val decidedAtIso: String,
    val actionType: String? = null,
    val exactContent: String? = null
)

data class StoredConsumedApprovalReceipt(
    val approvalId: String,
    val actionType: String,
    val consumedAtIso: String
)

interface ConductorRecordStore {
    fun saveTask(task: Task)
    fun tasks(): List<Task>

    fun saveOperationTimeline(timeline: OperationTimeline)
    fun operationTimelines(): List<OperationTimeline>

    fun saveGraphGrant(grant: GraphGrant)
    fun graphGrants(): List<GraphGrant>

    fun saveAppAgentGrant(grant: AppAgentGrant)
    fun appAgentGrants(): List<AppAgentGrant>

    fun saveGraphFact(fact: GraphFact)
    fun graphFacts(): List<GraphFact>

    fun saveApprovalDecision(decision: StoredApprovalDecision)
    fun approvalDecisions(): List<StoredApprovalDecision>

    fun saveConsumedApprovalReceipt(receipt: StoredConsumedApprovalReceipt)
    fun consumedApprovalReceipts(): List<StoredConsumedApprovalReceipt>

    fun saveAutonomyMode(mode: AutonomyMode)
    fun autonomyMode(): AutonomyMode?

    fun saveAccountSession(session: AccountSession)
    fun accountSessions(): List<AccountSession>

    fun saveConnectorAccount(account: ConnectedAccount)
    fun connectorAccounts(): List<ConnectedAccount>

    fun saveAppOperationSession(session: AppOperationSession)
    fun appOperationSessions(): List<AppOperationSession>

    fun saveAppOperationPlaybook(playbook: AppOperationPlaybook)
    fun appOperationPlaybooks(): List<AppOperationPlaybook>

    fun saveAppAgentDiscovery(discovery: AppAgentDiscovery)
    fun appAgentDiscoveries(): List<AppAgentDiscovery>

    fun enqueueAppOperation(item: AppOperationQueueItem)
    fun queuedAppOperations(): List<AppOperationQueueItem>
    fun resolveQueuedAppOperation(requestId: String)
    fun clearQueuedAppOperations()

    fun appendAudit(event: AuditEvent)
    fun auditEvents(): List<AuditEvent>
}
