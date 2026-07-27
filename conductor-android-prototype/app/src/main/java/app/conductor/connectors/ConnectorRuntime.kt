package app.conductor.connectors

import app.conductor.audit.AuditLedger
import app.conductor.graph.PersonalGraphStore
import app.conductor.storage.ConductorRecordStore

data class ConnectedAccount(
    val source: String,
    val accountId: String,
    val credentialHandle: String,
    val purposes: Set<String>
)

class ConnectorRuntime(
    private val auditLedger: AuditLedger,
    connectors: List<ConductorConnector>,
    private val recordStore: ConductorRecordStore? = null
) {
    private val connectorsBySource = connectors.associateBy { it.source }
    private val accounts = recordStore
        ?.connectorAccounts()
        ?.associateBy { accountKey(it.source, it.accountId) }
        ?.toMutableMap()
        ?: linkedMapOf()

    init {
        if (accounts.isNotEmpty()) {
            auditLedger.record("connector.accounts_restored", "${accounts.size} accounts")
        }
    }

    fun connect(account: ConnectedAccount) {
        accounts[accountKey(account.source, account.accountId)] = account
        recordStore?.saveConnectorAccount(account)
        auditLedger.record("connector.account_connected", accountKey(account.source, account.accountId))
    }

    fun connectedAccounts(): List<ConnectedAccount> =
        accounts.values.toList()

    fun hydrateGraph(graph: PersonalGraphStore, requests: List<ConnectorRequest>) {
        requests.forEach { request ->
            if (isGrantRevoked(request)) {
                auditLedger.record("connector.read_denied", "${request.source}:grant_revoked")
                return@forEach
            }
            val result = read(request)
            result.grants.forEach(graph::grantAccess)
            result.facts.forEach(graph::addFact)
        }
    }

    fun read(request: ConnectorRequest): ConnectorResult {
        val account = accounts["${request.source}:${request.accountId}"]
            ?: return denied(request, "missing_credential")

        if (!account.purposes.contains(request.purpose)) {
            return denied(request, "purpose_not_allowed")
        }

        val connector = connectorsBySource[request.source]
            ?: return denied(request, "missing_connector")

        val result = connector.read(request, account.credentialHandle)
        auditLedger.record("connector.read", "${request.source}:${request.purpose}:${result.facts.size} facts")
        return result
    }

    private fun denied(request: ConnectorRequest, reason: String): ConnectorResult {
        auditLedger.record("connector.read_denied", "${request.source}:${reason}")
        return ConnectorResult(status = "denied", facts = emptyList(), grants = emptyList(), reason = reason)
    }

    private fun accountKey(source: String, accountId: String): String = "$source:$accountId"

    private fun isGrantRevoked(request: ConnectorRequest): Boolean =
        recordStore
            ?.graphGrants()
            ?.any { grant ->
                grant.revoked &&
                    grant.source == request.source &&
                    grant.accountId == request.accountId &&
                    grant.purposes.contains(request.purpose)
            }
            ?: false
}
