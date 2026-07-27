package app.conductor.connectors

import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant

data class ConnectorRequest(
    val source: String,
    val accountId: String,
    val purpose: String,
    val params: Map<String, String> = emptyMap()
)

data class ConnectorResult(
    val status: String,
    val facts: List<GraphFact>,
    val grants: List<GraphGrant>,
    val reason: String? = null
)

interface ConductorConnector {
    val source: String
    fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult
}
