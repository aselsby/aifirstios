import test from "node:test";
import assert from "node:assert/strict";
import {
  AutonomyMode,
  Decision,
  approveAction,
  createInitialState,
  evaluatePolicy,
  runOutdoorIntent,
  setAutonomyMode
} from "../src/conductor-model.mjs";

test("outdoor intent gathers multi-app context and creates recommendations", () => {
  const state = runOutdoorIntent(createInitialState(), "Find something outdoors.");

  assert.equal(state.recommendations[0].title, "Outdoor Jazz At The Garden");
  assert.equal(state.plan.some((step) => step.app === "Facebook Events"), true);
  assert.equal(state.audit.some((event) => event.type === "context.accessed"), true);
});

test("Draft Only creates message draft but pauses send and calendar hold", () => {
  const state = runOutdoorIntent(createInitialState(), "Invite Maya.");

  assert.equal(state.apps.messages.drafts.length, 1);
  assert.equal(state.apps.messages.sent.length, 0);
  assert.equal(state.approvals.some((approval) => approval.step.actionType === "outbound_message.send"), true);
  assert.equal(state.approvals.some((approval) => approval.step.actionType === "calendar.create_hold"), true);
});

test("approving send executes the app action", () => {
  const state = runOutdoorIntent(createInitialState(), "Invite Maya.");
  const send = state.approvals.find((approval) => approval.step.actionType === "outbound_message.send");
  const approved = approveAction(state, send.id);

  assert.equal(approved.apps.messages.sent.length, 1);
  assert.equal(approved.audit.some((event) => event.type === "approval.granted"), true);
  assert.equal(approved.audit.some((event) => event.type === "tool.executed" && event.detail.includes("Send Invite")), true);
});

test("Ask Only blocks external side effects", () => {
  const base = setAutonomyMode(createInitialState(), AutonomyMode.ASK_ONLY);
  const state = runOutdoorIntent(base, "Invite Maya.");

  assert.equal(state.apps.messages.sent.length, 0);
  assert.equal(state.approvals.length, 0);
  assert.equal(state.audit.some((event) => event.type === "policy.blocked"), true);
});

test("sensitive and blocked policy decisions are deterministic", () => {
  assert.equal(evaluatePolicy({ actionType: "outbound_message.send", externalSideEffect: true }, AutonomyMode.TRUSTED_AUTO).decision, Decision.REQUIRE_APPROVAL);
  assert.equal(evaluatePolicy({ actionType: "public_post.create", externalSideEffect: true }, AutonomyMode.TRUSTED_AUTO).decision, Decision.REQUIRE_APPROVAL);
  assert.equal(evaluatePolicy({ actionType: "purchase.create", externalSideEffect: true }, AutonomyMode.TRUSTED_AUTO).decision, Decision.BLOCK);
});
