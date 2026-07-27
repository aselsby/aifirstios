import { PersonalGraph, seedOutdoorPlanningGraph } from "./personal-graph.mjs";

const graph = seedOutdoorPlanningGraph(new PersonalGraph({
  now: () => new Date("2026-07-27T10:45:00-05:00")
}));

const snapshot = graph.modelSnapshot({
  purpose: "activity_planning",
  sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts"]
});

console.log(JSON.stringify({
  snapshot,
  audit: graph.audit
}, null, 2));
