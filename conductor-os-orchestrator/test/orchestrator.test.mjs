import test from "node:test";
import assert from "node:assert/strict";
import { AutonomyMode } from "../../conductor-action-sdk/src/policy.mjs";
import { runEndToEndOutdoorTask } from "../src/orchestrator.mjs";

test("end-to-end task captures voice and gathers cross-app graph context", async () => {
  const result = await runEndToEndOutdoorTask();

  assert.equal(result.handoff.intentType, "outdoor_activity");
  assert.equal(result.context.appAgentId, "conductor.voice");
  assert.equal(result.context.facts.length, 4);
  assert.equal(result.selectedEvent.source, "facebook_events");
  assert.equal(result.selectedContact.source, "device_contacts");
});

test("end-to-end task drafts but pauses message send without approval", async () => {
  const result = await runEndToEndOutdoorTask();

  assert.equal(result.status, "awaiting_approval");
  assert.equal(result.actionResults.draft.status, "succeeded");
  assert.equal(result.actionResults.send.status, "awaiting_approval");
  assert.equal(result.operatorResults.draftUi.status, "succeeded");
  assert.equal(result.operatorResults.approvedSend, null);
});

test("end-to-end task executes approved send through app operator", async () => {
  const result = await runEndToEndOutdoorTask({ approveSend: true });

  assert.equal(result.status, "completed");
  assert.equal(result.actionResults.send.approval.exactContent, "Want to check out Outdoor Jazz At The Garden at 3:30 PM? Weather looks good and it is nearby.");
  assert.equal(result.operatorResults.approvedSend.status, "succeeded");
  assert.equal(result.operatorResults.approvedSend.verification.verified, true);
});

test("orchestrator audit includes voice, context, policy, action, approval, and operator events", async () => {
  const result = await runEndToEndOutdoorTask({ approveSend: true });
  const eventTypes = new Set(result.audit.map((event) => event.type));

  assert.equal(eventTypes.has("voice.handoff"), true);
  assert.equal(eventTypes.has("context.snapshot"), true);
  assert.equal(eventTypes.has("app_agent.snapshot_allowed"), true);
  assert.equal(eventTypes.has("policy.evaluated"), true);
  assert.equal(eventTypes.has("approval.queued"), true);
  assert.equal(eventTypes.has("agent_route.ready"), true);
  assert.equal(eventTypes.has("action.executed"), true);
  assert.equal(eventTypes.has("operator.succeeded"), true);
});

test("end-to-end task can hydrate context from connectors", async () => {
  const result = await runEndToEndOutdoorTask({ approveSend: true, useConnectors: true });
  const sources = new Set(result.context.facts.map((fact) => fact.source));
  const auditText = JSON.stringify(result.audit);

  assert.equal(result.status, "completed");
  assert.equal(result.context.facts.length, 5);
  assert.equal(sources.has("maps"), true);
  assert.equal(auditText.includes("connector.read"), true);
  assert.equal(auditText.includes("vault:"), false);
});

test("ask-only mode blocks cross-app model context before actions", async () => {
  const result = await runEndToEndOutdoorTask({ mode: AutonomyMode.ASK_ONLY });
  const auditText = JSON.stringify(result.audit);

  assert.equal(result.status, "context_blocked");
  assert.equal(result.context.facts.length, 0);
  assert.equal(auditText.includes("app_agent.snapshot_denied"), true);
  assert.deepEqual(result.actionResults, {});
  assert.deepEqual(result.operatorResults, {});
});
