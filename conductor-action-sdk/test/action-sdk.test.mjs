import test from "node:test";
import assert from "node:assert/strict";
import { ApprovalMode, Risk, sampleActions, validateActionManifest } from "../src/action-manifest.mjs";
import { describeAutonomyProfile } from "../src/autonomy-profile.mjs";
import { ActionRuntime } from "../src/executor.mjs";
import { AutonomyMode, Decision, decideAction, decideDataSnapshot } from "../src/policy.mjs";

test("sample action manifests are valid", () => {
  for (const action of sampleActions) {
    assert.deepEqual(validateActionManifest(action), { valid: true, errors: [] });
  }
});

test("messages.send requires approval even in trusted mode", () => {
  const action = sampleActions.find((item) => item.id === "messages.send");
  const decision = decideAction(action, AutonomyMode.TRUSTED_AUTO);

  assert.equal(action.approval, ApprovalMode.ALWAYS);
  assert.equal(decision.decision, Decision.REQUIRE_APPROVAL);
});

test("draft message can execute without approval", () => {
  const runtime = new ActionRuntime({ mode: AutonomyMode.DRAFT_ONLY });
  const result = runtime.execute("messages.create_draft", {
    recipient: "Maya Chen",
    body: "Want to go?"
  });

  assert.equal(result.status, "succeeded");
  assert.equal(result.verification.status, "verified");
});

test("send pauses before approval and executes after approval", () => {
  const runtime = new ActionRuntime({ mode: AutonomyMode.DRAFT_ONLY });
  const input = { recipient: "Maya Chen", exactBody: "Want to go?" };
  const first = runtime.execute("messages.send", input);
  const second = runtime.execute("messages.send", input, {
    status: "approved",
    approvalId: first.approval.id,
    exactContent: first.approval.exactContent
  });

  assert.equal(first.status, "awaiting_approval");
  assert.equal(first.approval.exactContent, "Want to go?");
  assert.equal(second.status, "succeeded");
  assert.equal(second.verification.method, "message_receipt");
});

test("approval receipts require exact content and cannot be replayed", () => {
  const runtime = new ActionRuntime({ mode: AutonomyMode.DRAFT_ONLY });
  const input = { recipient: "Maya Chen", exactBody: "Want to go?" };
  const queued = runtime.execute("messages.send", input);
  const mismatch = runtime.execute("messages.send", input, {
    status: "approved",
    approvalId: queued.approval.id,
    exactContent: "Different text"
  });
  const approval = {
    status: "approved",
    approvalId: queued.approval.id,
    exactContent: queued.approval.exactContent
  };
  const first = runtime.execute("messages.send", input, approval);
  const replay = runtime.execute("messages.send", input, approval);

  assert.equal(mismatch.status, "awaiting_approval");
  assert.equal(mismatch.reason, "exact_approval_mismatch");
  assert.equal(first.status, "succeeded");
  assert.equal(replay.status, "awaiting_approval");
  assert.equal(replay.reason, "approval_receipt_replayed");
});

test("public post requires exact approval instead of being globally blocked", () => {
  const action = sampleActions.find((item) => item.id === "facebook.post");
  const runtime = new ActionRuntime({ mode: AutonomyMode.TRUSTED_AUTO });
  const input = { exactBody: "Anyone want to go to Outdoor Jazz At The Garden?" };
  const first = runtime.execute("facebook.post", input);
  const second = runtime.execute("facebook.post", input, {
    status: "approved",
    approvalId: first.approval.id,
    exactContent: first.approval.exactContent
  });

  assert.equal(decideAction(action, AutonomyMode.TRUSTED_AUTO).decision, Decision.REQUIRE_APPROVAL);
  assert.equal(first.status, "awaiting_approval");
  assert.equal(first.approval.exactContent, input.exactBody);
  assert.equal(second.status, "succeeded");
  assert.equal(second.verification.method, "post_receipt");
});

test("blocked action types cannot execute", () => {
  const runtime = new ActionRuntime({
    mode: AutonomyMode.TRUSTED_AUTO,
    actions: [
      ...sampleActions,
      {
        id: "payments.buy_ticket",
        app: "Ticketing",
        actionType: "purchase.create",
        description: "Buy a ticket.",
        risk: Risk.HIGH,
        externalSideEffect: true,
        approval: ApprovalMode.POLICY,
        inputSchema: { required: ["eventId", "amount"] },
        outputSchema: { required: ["receiptId"] },
        verification: "receipt_exists"
      }
    ]
  });

  const result = runtime.execute("payments.buy_ticket", { eventId: "event_1", amount: 48 });
  assert.equal(result.status, "blocked");
  assert.equal(result.policy.decision, Decision.BLOCK);
});

test("unknown or malformed actions cannot execute", () => {
  const runtime = new ActionRuntime({ mode: AutonomyMode.TRUSTED_AUTO });

  assert.equal(runtime.execute("unknown.action", {}).status, "blocked");
  assert.equal(runtime.execute("messages.send", { recipient: "Maya Chen" }).status, "blocked");
});

test("autonomy profiles describe data and side-effect limits", () => {
  const askOnly = describeAutonomyProfile(AutonomyMode.ASK_ONLY);
  const trusted = describeAutonomyProfile(AutonomyMode.TRUSTED_AUTO);

  assert.equal(askOnly.allowsDataSnapshots, false);
  assert.equal(askOnly.allowsExternalSideEffects, false);
  assert.equal(trusted.allowsDataSnapshots, true);
  assert.equal(trusted.autoRisk.includes(Risk.MEDIUM), true);
});

test("data snapshots require mode permission and app-agent grant", () => {
  const grant = {
    sources: ["google_calendar", "facebook_events"],
    revoked: false
  };

  assert.equal(decideDataSnapshot({ appAgentGrant: grant, requestedSources: ["google_calendar"] }, AutonomyMode.DRAFT_ONLY).decision, Decision.ALLOW);
  assert.equal(decideDataSnapshot({ appAgentGrant: grant, requestedSources: ["google_calendar"] }, AutonomyMode.ASK_ONLY).decision, Decision.BLOCK);
  assert.equal(decideDataSnapshot({ appAgentGrant: { ...grant, revoked: true }, requestedSources: ["google_calendar"] }, AutonomyMode.DRAFT_ONLY).decision, Decision.BLOCK);
  assert.equal(decideDataSnapshot({ appAgentGrant: grant, requestedSources: ["photos"] }, AutonomyMode.DRAFT_ONLY).decision, Decision.BLOCK);
});
