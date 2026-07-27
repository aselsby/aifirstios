package app.conductor.connectors

import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.storage.ConductorRecordStore

fun defaultOutdoorConnectorRuntime(
    auditLedger: app.conductor.audit.AuditLedger,
    recordStore: ConductorRecordStore? = null
): ConnectorRuntime {
    return ConnectorRuntime(
        auditLedger = auditLedger,
        recordStore = recordStore,
        connectors = listOf(
            CalendarConnector(),
            WeatherConnector(),
            FacebookEventsConnector(),
            ContactsConnector(),
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

private class CalendarConnector : ConductorConnector {
    override val source = "google_calendar"
    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult =
        result(
            request = request,
            fact = GraphFact(
                id = "android_calendar_free",
                type = "calendar_availability",
                source = source,
                accountId = request.accountId,
                summary = "Free from 2:30 PM to 5:30 PM; dinner hold at 7:00 PM.",
                redactedSummary = "Free from 2:30 PM to 5:30 PM.",
                sensitivity = Sensitivity.PRIVATE,
                allowedPurposes = setOf("activity_planning", "scheduling"),
                expiresAtIso = "2026-08-26T10:45:00-05:00"
            )
        )
}

private class WeatherConnector : ConductorConnector {
    override val source = "weather_provider"
    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult =
        result(
            request = request,
            fact = GraphFact(
                id = "android_weather_clear",
                type = "weather_hourly",
                source = source,
                accountId = request.accountId,
                summary = "Clear after 1 PM, 78 F, low wind.",
                sensitivity = Sensitivity.PUBLIC,
                allowedPurposes = setOf("activity_planning"),
                expiresAtIso = "2026-07-28T10:45:00-05:00"
            )
        )
}

private class FacebookEventsConnector : ConductorConnector {
    override val source = "facebook_events"
    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult =
        result(
            request = request,
            fact = GraphFact(
                id = "android_event_jazz",
                type = "event_candidate",
                source = source,
                accountId = request.accountId,
                summary = "Outdoor Jazz At The Garden at 3:30 PM, 2.4 miles away, free.",
                sensitivity = Sensitivity.PERSONAL,
                allowedPurposes = setOf("activity_planning"),
                expiresAtIso = "2026-07-27T17:30:00-05:00"
            )
        )
}

private class ContactsConnector : ConductorConnector {
    override val source = "device_contacts"
    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult =
        result(
            request = request,
            fact = GraphFact(
                id = "android_contact_maya",
                type = "contact_preference",
                source = source,
                accountId = request.accountId,
                summary = "Maya Chen prefers Messages and is often invited to outdoor events.",
                redactedSummary = "Selected contact prefers Messages.",
                sensitivity = Sensitivity.PRIVATE,
                allowedPurposes = setOf("activity_planning", "messaging"),
                expiresAtIso = "2026-08-26T10:45:00-05:00"
            )
        )
}

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
                summary = "Outdoor Jazz At The Garden is about 12 minutes away.",
                sensitivity = Sensitivity.PERSONAL,
                allowedPurposes = setOf("activity_planning", "navigation"),
                expiresAtIso = "2026-07-28T10:45:00-05:00"
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
                expiresAtIso = "2026-07-28T10:45:00-05:00"
            )
        )
    )
