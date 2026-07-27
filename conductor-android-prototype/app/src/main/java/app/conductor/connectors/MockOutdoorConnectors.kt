package app.conductor.connectors

import android.content.Context
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore

fun defaultOutdoorConnectorRuntime(
    auditLedger: app.conductor.audit.AuditLedger,
    recordStore: ConductorRecordStore? = null,
    context: Context? = null
): ConnectorRuntime {
    return ConnectorRuntime(
        auditLedger = auditLedger,
        recordStore = recordStore,
        connectors = listOf(
            DeviceCalendarConnector(context),
            OpenMeteoWeatherConnector(context),
            NearbyOutdoorEventsConnector(context),
            DeviceContactsConnector(context),
            MapsConnector()
        )
    ).apply {
        connect(ConnectedAccount("google_calendar", "personal", "vault:calendar:personal", setOf("activity_planning", "scheduling")))
        connect(ConnectedAccount("weather_provider", "device", "vault:weather:device", setOf("activity_planning")))
        connect(ConnectedAccount("facebook_events", "personal", "vault:facebook:personal", setOf("activity_planning")))
        connect(ConnectedAccount("device_contacts", "device", "vault:contacts:device", setOf("activity_planning", "messaging")))
        connect(ConnectedAccount("maps", "device", "vault:maps:device", setOf("activity_planning", "navigation")))
    }
}

fun outdoorPlanningRequests(): List<ConnectorRequest> = listOf(
    ConnectorRequest("google_calendar", "personal", "activity_planning"),
    ConnectorRequest("weather_provider", "device", "activity_planning"),
    ConnectorRequest("facebook_events", "personal", "activity_planning"),
    ConnectorRequest("device_contacts", "device", "activity_planning"),
    ConnectorRequest("maps", "device", "activity_planning")
)

private class MapsConnector : ConductorConnector {
    override val source = "maps"
    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult =
        result(
            request = request,
            fact = GraphFact(
                id = "android_route_hint",
                type = "route_hint",
                source = source,
                accountId = request.accountId,
                summary = "Top outdoor candidates are about 10-15 minutes away by car.",
                sensitivity = Sensitivity.PERSONAL,
                allowedPurposes = setOf("activity_planning", "navigation"),
                expiresAtIso = SystemClock.plusHours(8)
            )
        )
}

private fun result(request: ConnectorRequest, fact: GraphFact): ConnectorResult =
    ConnectorResult(
        status = "ok",
        facts = listOf(fact),
        grants = listOf(
            GraphGrant(
                id = "grant_${request.source}_${request.purpose}",
                source = request.source,
                accountId = request.accountId,
                purposes = setOf(request.purpose),
                expiresAtIso = SystemClock.plusHours(8)
            )
        )
    )
