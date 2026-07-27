import test from "node:test";
import assert from "node:assert/strict";
import {
  ConnectorRuntime,
  createDefaultConnectorRuntime,
  createMockConnectors,
  outdoorPlanningRequests
} from "../src/connectors.mjs";

test("default connectors hydrate graph with outdoor planning facts", async () => {
  const runtime = createDefaultConnectorRuntime({
    now: () => new Date("2026-07-27T10:45:00-05:00")
  });
  const graph = await runtime.hydrateGraph({ requests: outdoorPlanningRequests() });
  const snapshot = graph.modelSnapshot({
    purpose: "activity_planning",
    sources: ["google_calendar", "weather_provider", "facebook_events", "device_contacts", "maps"]
  });

  assert.equal(snapshot.facts.length, 5);
  assert.equal(snapshot.facts.some((fact) => fact.source === "facebook_events"), true);
  assert.equal(snapshot.facts.some((fact) => fact.source === "maps"), true);
});

test("connector denies read when purpose is not connected", async () => {
  const runtime = createDefaultConnectorRuntime();
  const result = await runtime.read({
    source: "google_calendar",
    accountId: "personal",
    purpose: "ad_targeting"
  });

  assert.equal(result.status, "denied");
  assert.equal(result.reason, "purpose_not_allowed");
});

test("connector denies read without credential", async () => {
  const runtime = new ConnectorRuntime();
  for (const connector of createMockConnectors()) runtime.registerConnector(connector);
  const result = await runtime.read({
    source: "google_calendar",
    accountId: "personal",
    purpose: "activity_planning"
  });

  assert.equal(result.status, "denied");
  assert.equal(result.reason, "missing_credential");
});

test("connector audit never exposes credential handles", async () => {
  const runtime = createDefaultConnectorRuntime();
  await runtime.read({
    source: "facebook_events",
    accountId: "personal",
    purpose: "activity_planning"
  });

  const auditText = JSON.stringify(runtime.audit);
  assert.equal(auditText.includes("vault:"), false);
});

test("normalized facts include graph-required fields", async () => {
  const runtime = createDefaultConnectorRuntime();
  const result = await runtime.read({
    source: "device_contacts",
    accountId: "device",
    purpose: "activity_planning"
  });
  const fact = result.facts[0];

  for (const field of ["id", "type", "source", "accountId", "summary", "sensitivity", "allowedPurposes"]) {
    assert.equal(fact[field] !== undefined, true);
  }
});
