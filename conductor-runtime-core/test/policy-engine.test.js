import test from "node:test";
import assert from "node:assert/strict";
import { AutonomyMode, Decision, Risk } from "../src/domain.js";
import { evaluatePolicy } from "../src/policy-engine.js";

test("Draft Only allows unsent drafts", () => {
  const decision = evaluatePolicy({
    actionType: "outbound_message.create_draft",
    risk: Risk.LOW,
    externalSideEffect: false
  }, { mode: AutonomyMode.DRAFT_ONLY });

  assert.equal(decision.decision, Decision.ALLOW);
});

test("Draft Only requires exact approval before sending a message", () => {
  const decision = evaluatePolicy({
    actionType: "outbound_message.send",
    risk: Risk.MEDIUM,
    externalSideEffect: true
  }, { mode: AutonomyMode.DRAFT_ONLY });

  assert.equal(decision.decision, Decision.REQUIRE_APPROVAL);
  assert.match(decision.reason, /approval/i);
});

test("public posting requires exact approval", () => {
  const decision = evaluatePolicy({
    actionType: "public_post.create",
    risk: Risk.HIGH,
    externalSideEffect: true
  }, { mode: AutonomyMode.TRUSTED_AUTO });

  assert.equal(decision.decision, Decision.REQUIRE_APPROVAL);
  assert.match(decision.reason, /approval/i);
});

test("MVP blocks purchases and destructive account actions", () => {
  for (const actionType of ["purchase.create", "account_security.change", "data.delete"]) {
    const decision = evaluatePolicy({
      actionType,
      risk: Risk.HIGH,
      externalSideEffect: true
    }, { mode: AutonomyMode.TRUSTED_AUTO });

    assert.equal(decision.decision, Decision.BLOCK);
  }
});

test("Ask Only blocks external actions", () => {
  const decision = evaluatePolicy({
    actionType: "calendar.read",
    risk: Risk.LOW,
    externalSideEffect: false
  }, { mode: AutonomyMode.ASK_ONLY });

  assert.equal(decision.decision, Decision.BLOCK);
});
