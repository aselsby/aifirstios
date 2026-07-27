import { createDefaultConnectorRuntime, outdoorPlanningRequests } from "./connectors.mjs";

const runtime = createDefaultConnectorRuntime({
  now: () => new Date("2026-07-27T10:45:00-05:00")
});

const graph = await runtime.hydrateGraph({
  requests: outdoorPlanningRequests()
});
const snapshot = graph.modelSnapshot({
  purpose: "activity_planning",
  sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts", "maps"]
});

console.log(JSON.stringify({
  snapshot,
  connectorAudit: runtime.audit,
  graphAudit: graph.audit
}, null, 2));
