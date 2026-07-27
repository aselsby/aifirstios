package app.conductor.context

import app.conductor.audit.AuditLedger
import app.conductor.connectors.defaultOutdoorConnectorRuntime
import app.conductor.connectors.outdoorPlanningRequests
import app.conductor.graph.AppAgentGrant
import app.conductor.graph.PersonalGraphStore
import app.conductor.graph.UserPreferenceMemory
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.ContextBundle
import app.conductor.runtime.SystemClock
import app.conductor.runtime.Task
import app.conductor.storage.ConductorRecordStore

class MockContextBroker(
    private val auditLedger: AuditLedger,
    private val recordStore: ConductorRecordStore? = null
) {
    fun gatherOutdoorActivityContext(task: Task, autonomyMode: AutonomyMode): ContextBundle {
        auditLedger.record(
            type = "context.gathered",
            detail = "calendar, weather, facebook_events_and_web, contacts, maps for task ${task.id}"
        )

        if (autonomyMode == AutonomyMode.ASK_ONLY) {
            auditLedger.record("app_agent.snapshot_denied", "conductor.voice:activity_planning:ASK_ONLY")
            return ContextBundle(
                id = "ctx_${task.id}",
                taskId = task.id,
                purpose = "activity_planning",
                items = emptyMap()
            )
        }

        val graph = PersonalGraphStore(auditLedger, recordStore)
        UserPreferenceMemory(auditLedger).captureActivityPreference(task.goal, graph)
        grantVoiceActivityAccess(graph)
        val retainedContext = graph.toContextBundleForAppAgent(
            task = task,
            appAgentId = "conductor.voice",
            autonomyMode = autonomyMode,
            purpose = "activity_planning",
            sources = requiredOutdoorSources
        )
        if (retainedContext.hasAllOutdoorSources()) {
            auditLedger.record("context.restored_from_graph", "${retainedContext.items.size} retained items")
            return retainedContext
        }

        auditLedger.record("context.cache_miss", "hydrating connectors for ${task.id}")
        defaultOutdoorConnectorRuntime(auditLedger, recordStore).hydrateGraph(
            graph = graph,
            requests = outdoorPlanningRequests()
        )
        UserPreferenceMemory(auditLedger).captureActivityPreference(task.goal, graph)
        grantVoiceActivityAccess(graph)
        return graph.toContextBundleForAppAgent(
            task = task,
            appAgentId = "conductor.voice",
            autonomyMode = autonomyMode,
            purpose = "activity_planning",
            sources = requiredOutdoorSources
        )
    }

    private fun grantVoiceActivityAccess(graph: PersonalGraphStore) {
        graph.grantAppAgentAccess(
            AppAgentGrant(
                id = "agent_grant_conductor_activity",
                appAgentId = "conductor.voice",
                packageName = "app.conductor.prototype",
                purposes = setOf("activity_planning"),
                sources = requiredOutdoorSources,
                expiresAtIso = SystemClock.plusHours(24)
            )
        )
    }

    private fun ContextBundle.hasAllOutdoorSources(): Boolean =
        items.keys.containsAll(requiredOutdoorContextKeys)

    private companion object {
        val requiredOutdoorSources = setOf(
            "google_calendar",
            "weather_provider",
            "facebook_events",
            "device_contacts",
            "maps",
            "conductor_memory"
        )
        val requiredOutdoorContextKeys = setOf("calendar", "weather", "events", "contacts", "maps")
    }
}
