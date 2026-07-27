import test from "node:test";
import assert from "node:assert/strict";
import { AutonomyMode } from "../../conductor-action-sdk/src/policy.mjs";
import { PersonalGraph, Sensitivity, seedOutdoorPlanningGraph } from "../src/personal-graph.mjs";

test("outdoor planning snapshot includes approved cross-app facts", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));

  const snapshot = graph.modelSnapshot({
    purpose: "activity_planning",
    sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts"]
  });

  assert.equal(snapshot.facts.length, 4);
  assert.equal(snapshot.facts.some((fact) => fact.source === "google_calendar"), true);
  assert.equal(snapshot.facts.some((fact) => fact.source === "facebook_events"), true);
});

test("purpose mismatch denies facts even when the source has data", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));

  const snapshot = graph.modelSnapshot({
    purpose: "ad_targeting",
    sources: ["google_calendar", "facebook_events"]
  });

  assert.equal(snapshot.facts.length, 0);
  assert.equal(snapshot.denied.length, 2);
  assert.equal(snapshot.denied.every((item) => item.reason === "purpose_not_allowed"), true);
});

test("revoked grant prevents future access", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));

  graph.revokeGrant("grant_events_activity");
  const snapshot = graph.modelSnapshot({
    purpose: "activity_planning",
    sources: ["facebook_events"]
  });

  assert.equal(snapshot.facts.length, 0);
  assert.equal(snapshot.denied[0].reason, "missing_or_revoked_grant");
});

test("expired facts are omitted and purgeable", () => {
  const graph = new PersonalGraph({
    now: () => new Date("2026-07-27T12:00:00-05:00")
  });
  graph.grantAccess({
    id: "grant_weather",
    source: "weather_provider",
    accountId: "device",
    purposes: ["activity_planning"]
  });
  graph.addFact({
    id: "expired_weather",
    type: "weather_hourly",
    source: "weather_provider",
    accountId: "device",
    summary: "Old forecast.",
    sensitivity: Sensitivity.PUBLIC,
    allowedPurposes: ["activity_planning"],
    expiresAt: "2026-07-27T11:00:00-05:00"
  });

  assert.equal(graph.query({ purpose: "activity_planning", sources: ["weather_provider"] }).facts.length, 0);
  assert.equal(graph.purgeExpired(), 1);
});

test("private facts use redacted summaries in model snapshots", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));
  const snapshot = graph.modelSnapshot({
    purpose: "activity_planning",
    sources: ["google_calendar"]
  });

  assert.equal(snapshot.facts[0].summary, "Free from 2:30 PM to 5:30 PM.");
});

test("app-agent grant allows bounded cross-app model snapshot", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));
  const snapshot = graph.modelSnapshotForAppAgent({
    purpose: "activity_planning",
    sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts"]
  }, {
    appAgentId: "conductor.voice"
  });

  assert.equal(snapshot.appAgentId, "conductor.voice");
  assert.equal(snapshot.facts.length, 4);
  assert.equal(snapshot.denied.length, 0);
});

test("app-agent snapshot denies ungranted agent and ungranted source", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));
  const unknownAgent = graph.modelSnapshotForAppAgent({
    purpose: "activity_planning",
    sources: ["google_calendar"]
  }, {
    appAgentId: "unknown.app"
  });
  const sourceOutsideGrant = graph.modelSnapshotForAppAgent({
    purpose: "activity_planning",
    sources: ["google_calendar", "photos"]
  }, {
    appAgentId: "conductor.voice"
  });

  assert.equal(unknownAgent.facts.length, 0);
  assert.equal(unknownAgent.denied[0].reason, "missing_or_revoked_app_agent_grant");
  assert.equal(sourceOutsideGrant.facts.length, 0);
  assert.deepEqual(sourceOutsideGrant.denied[0].sources, ["google_calendar", "photos"]);
});

test("revoked app-agent grant blocks future snapshots", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));

  assert.equal(graph.revokeAppAgentGrant("agent_grant_conductor_activity"), true);
  graph.grantAppAgentAccess({
    id: "agent_grant_conductor_activity",
    appAgentId: "conductor.voice",
    packageName: "app.conductor.prototype",
    purposes: ["activity_planning"],
    sources: ["facebook_events"]
  });
  const snapshot = graph.modelSnapshotForAppAgent({
    purpose: "activity_planning",
    sources: ["facebook_events"]
  }, {
    appAgentId: "conductor.voice"
  });

  assert.equal(snapshot.facts.length, 0);
  assert.equal(snapshot.denied[0].reason, "missing_or_revoked_app_agent_grant");
});

test("ask-only autonomy blocks app-agent model snapshots", () => {
  const graph = seedOutdoorPlanningGraph(new PersonalGraph({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  }));
  const snapshot = graph.modelSnapshotForAppAgent({
    purpose: "activity_planning",
    sources: ["google_calendar"]
  }, {
    appAgentId: "conductor.voice",
    autonomyMode: AutonomyMode.ASK_ONLY
  });

  assert.equal(snapshot.facts.length, 0);
  assert.equal(snapshot.denied[0].reason, "ask_only blocks model data snapshots.");
});
