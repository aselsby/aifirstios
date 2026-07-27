package app.conductor.graph

import app.conductor.audit.AuditLedger
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.ContextBundle
import app.conductor.runtime.ContextItem
import app.conductor.runtime.SystemClock
import app.conductor.runtime.Task
import app.conductor.storage.ConductorRecordStore

enum class Sensitivity {
    PUBLIC,
    PERSONAL,
    PRIVATE,
    SECRET
}

data class GraphGrant(
    val id: String,
    val source: String,
    val accountId: String,
    val purposes: Set<String>,
    val revoked: Boolean = false,
    val expiresAtIso: String? = null
) {
    fun isExpired(nowIso: String): Boolean =
        expiresAtIso?.let { it <= nowIso } ?: false
}

data class AppAgentGrant(
    val id: String,
    val appAgentId: String,
    val packageName: String,
    val purposes: Set<String>,
    val sources: Set<String>,
    val revoked: Boolean = false,
    val expiresAtIso: String? = null
) {
    fun isExpired(nowIso: String): Boolean =
        expiresAtIso?.let { it <= nowIso } ?: false
}

data class GraphFact(
    val id: String,
    val type: String,
    val source: String,
    val accountId: String,
    val summary: String,
    val redactedSummary: String? = null,
    val sensitivity: Sensitivity,
    val allowedPurposes: Set<String>,
    val expiresAtIso: String? = null
) {
    fun isExpired(nowIso: String): Boolean =
        expiresAtIso?.let { it <= nowIso } ?: false
}

class PersonalGraphStore(
    private val auditLedger: AuditLedger,
    private val recordStore: ConductorRecordStore? = null,
    private val nowIso: () -> String = { SystemClock.nowIso() }
) {
    private val grants = recordStore
        ?.graphGrants()
        ?.associateBy { it.id }
        ?.toMutableMap()
        ?: linkedMapOf()
    private val appAgentGrants = recordStore
        ?.appAgentGrants()
        ?.associateBy { it.id }
        ?.toMutableMap()
        ?: linkedMapOf()
    private val facts = recordStore
        ?.graphFacts()
        ?.associateBy { it.id }
        ?.toMutableMap()
        ?: linkedMapOf()

    fun grantAccess(grant: GraphGrant) {
        val persisted = grants[grant.id]
        if (persisted?.revoked == true) {
            auditLedger.record("graph.grant_preserved_revoked", "${grant.source}:${grant.purposes.joinToString(",")}")
            return
        }

        val mergedGrant = persisted?.copy(
            purposes = persisted.purposes + grant.purposes,
            expiresAtIso = grant.expiresAtIso ?: persisted.expiresAtIso,
            revoked = false
        ) ?: grant
        grants[mergedGrant.id] = mergedGrant
        recordStore?.saveGraphGrant(mergedGrant)
        auditLedger.record("graph.grant_created", "${grant.source}:${grant.purposes.joinToString(",")}")
    }

    fun addFact(fact: GraphFact) {
        facts[fact.id] = fact
        recordStore?.saveGraphFact(fact)
        auditLedger.record("graph.fact_added", "${fact.type}:${fact.source}")
    }

    fun grantAppAgentAccess(grant: AppAgentGrant) {
        val persisted = appAgentGrants[grant.id]
        if (persisted?.revoked == true) {
            auditLedger.record("app_agent.grant_preserved_revoked", "${grant.appAgentId}:${grant.purposes.joinToString(",")}")
            return
        }

        val mergedGrant = persisted?.copy(
            purposes = persisted.purposes + grant.purposes,
            sources = persisted.sources + grant.sources,
            expiresAtIso = grant.expiresAtIso ?: persisted.expiresAtIso,
            revoked = false
        ) ?: grant
        appAgentGrants[mergedGrant.id] = mergedGrant
        recordStore?.saveAppAgentGrant(mergedGrant)
        auditLedger.record("app_agent.grant_created", "${grant.appAgentId}:${grant.purposes.joinToString(",")}")
    }

    fun toContextBundle(task: Task, purpose: String, sources: Set<String>): ContextBundle {
        val allowed = facts.values
            .filter { fact -> sources.contains(fact.source) }
            .filter { fact -> !fact.isExpired(nowIso()) }
            .filter { fact -> fact.allowedPurposes.contains(purpose) }
            .mapNotNull { fact ->
                val grant = matchingGrant(fact, purpose) ?: return@mapNotNull null
                fact to grant
            }

        auditLedger.record("graph.queried", "$purpose: ${allowed.size} allowed")

        return ContextBundle(
            id = "ctx_${task.id}",
            taskId = task.id,
            purpose = purpose,
            items = allowed.associate { (fact, grant) ->
                semanticSourceKey(fact.source) to ContextItem(
                    source = fact.source,
                    type = fact.type,
                    summary = redact(fact),
                    sensitivity = fact.sensitivity.name.lowercase(),
                    accountId = fact.accountId,
                    factId = fact.id,
                    allowedPurpose = purpose,
                    expiresAtIso = fact.expiresAtIso.orEmpty(),
                    freshnessStatus = fact.expiresAtIso?.let { "fresh_until=$it" } ?: "fresh_without_expiry",
                    baseGrantId = grant.id
                )
            }
        )
    }

    fun toContextBundleForAppAgent(
        task: Task,
        appAgentId: String,
        autonomyMode: AutonomyMode = AutonomyMode.DRAFT_ONLY,
        purpose: String,
        sources: Set<String>
    ): ContextBundle {
        if (autonomyMode == AutonomyMode.ASK_ONLY) {
            auditLedger.record("app_agent.snapshot_denied", "$appAgentId:$purpose:ASK_ONLY")
            return ContextBundle(
                id = "ctx_${task.id}",
                taskId = task.id,
                purpose = purpose,
                items = emptyMap()
            )
        }

        val appAgentGrant = matchingAppAgentGrant(appAgentId, purpose, sources)
        if (appAgentGrant == null) {
            auditLedger.record("app_agent.snapshot_denied", "$appAgentId:$purpose")
            return ContextBundle(
                id = "ctx_${task.id}",
                taskId = task.id,
                purpose = purpose,
                items = emptyMap()
            )
        }

        val bundle = toContextBundle(task, purpose, sources)
        val appAgentScopedBundle = bundle.copy(
            items = bundle.items.mapValues { (_, item) ->
                item.copy(
                    appAgentId = appAgentId,
                    appAgentGrantId = appAgentGrant.id
                )
            }
        )
        auditLedger.record("app_agent.snapshot_allowed", "$appAgentId:$purpose:${bundle.items.size} items")
        return appAgentScopedBundle
    }

    private fun matchingGrant(fact: GraphFact, purpose: String): GraphGrant? =
        grants.values.firstOrNull { grant ->
            !grant.revoked &&
                !grant.isExpired(nowIso()) &&
                grant.source == fact.source &&
                grant.accountId == fact.accountId &&
                grant.purposes.contains(purpose)
        }

    private fun matchingAppAgentGrant(appAgentId: String, purpose: String, sources: Set<String>): AppAgentGrant? =
        appAgentGrants.values.firstOrNull { grant ->
            !grant.revoked &&
                !grant.isExpired(nowIso()) &&
                grant.appAgentId == appAgentId &&
                grant.purposes.contains(purpose) &&
                grant.sources.containsAll(sources)
        }

    private fun hasAppAgentGrant(appAgentId: String, purpose: String, sources: Set<String>): Boolean =
        matchingAppAgentGrant(appAgentId, purpose, sources) != null

    private fun redact(fact: GraphFact): String =
        when {
            fact.sensitivity == Sensitivity.SECRET -> "[redacted secret]"
            fact.sensitivity == Sensitivity.PRIVATE && fact.redactedSummary != null -> fact.redactedSummary
            else -> fact.summary
        }

    private fun semanticSourceKey(source: String): String =
        when (source) {
            "google_calendar" -> "calendar"
            "weather_provider" -> "weather"
            "facebook_events" -> "events"
            "device_contacts" -> "contacts"
            "conductor_memory" -> "preferences"
            else -> source
        }
}
