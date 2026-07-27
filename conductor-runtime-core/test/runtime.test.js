import test from "node:test";
import assert from "node:assert/strict";
import { AuditLog } from "../src/audit-log.js";
import { Risk } from "../src/domain.js";
import { Executor } from "../src/executor.js";
import { runOutdoorActivityWorkflow } from "../src/runtime.js";
import { createToolRegistry } from "../src/tool-registry.js";

test("outdoor workflow gathers cross-app context and ranks an event", async () => {
  const result = await runOutdoorActivityWorkflow();

  assert.equal(result.context.items.calendar.type, "calendar_availability");
  assert.equal(result.context.items.weather.type, "weather_hourly");
  assert.equal(result.context.items.events.type, "events_nearby");
  assert.equal(result.context.items.contacts.type, "contact_candidates");
  assert.equal(result.plan.recommendation.title, "Outdoor Jazz At The Garden");
});

test("message send pauses in approval queue before execution", async () => {
  const result = await runOutdoorActivityWorkflow();
  const pending = result.firstPassResults.filter((item) => item.status === "awaiting_approval");
  const queueIndex = result.audit.findIndex((event) => (
    event.type === "approval.queued" && event.detail.stepId === "send_invite"
  ));
  const grantIndex = result.audit.findIndex((event) => (
    event.type === "approval.granted" && event.detail.approvalId === "approval_send_invite"
  ));
  const sendIndex = result.audit.findIndex((event) => (
    event.type === "tool.executed" && event.detail.stepId === "send_invite"
  ));

  assert.equal(pending.length, 1);
  assert.equal(pending[0].stepId, "send_invite");
  assert.equal(pending[0].approval.exactContent.includes("Outdoor Jazz"), true);
  assert.equal(queueIndex >= 0, true);
  assert.equal(grantIndex > queueIndex, true);
  assert.equal(sendIndex > grantIndex, true);
});

test("approved message send is executed and verified", async () => {
  const result = await runOutdoorActivityWorkflow();

  assert.equal(result.approvedResults.length, 1);
  assert.equal(result.approvedResults[0].stepId, "send_invite");
  assert.equal(result.approvedResults[0].verification.status, "verified");
});

test("approved public post executes only with matching exact content", async () => {
  const auditLog = new AuditLog();
  const executor = new Executor({
    tools: createToolRegistry(auditLog),
    auditLog,
    userPolicy: { mode: "trusted_auto" }
  });
  const step = {
    id: "post_event",
    title: "Post outdoor plan",
    tool: "social.public_post",
    actionType: "public_post.create",
    risk: Risk.HIGH,
    externalSideEffect: true,
    input: {
      exactBody: "Heading to Outdoor Jazz At The Garden at 3:30 PM."
    }
  };

  const queued = await executor.runStep(step);
  const mismatched = await executor.runStep(step, {
    status: "approved",
    approvalId: queued.approval.id,
    exactContent: "Edited post text"
  });
  const approved = await executor.runStep(step, {
    status: "approved",
    approvalId: queued.approval.id,
    exactContent: queued.approval.exactContent
  });

  assert.equal(queued.status, "awaiting_approval");
  assert.equal(mismatched.status, "awaiting_approval");
  assert.equal(approved.status, "succeeded");
  assert.equal(approved.verification.method, "public_post_receipt");
  assert.equal(auditLog.all().some((event) => event.type === "approval.rejected"), true);
});

test("audit log records context, policy, approval, execution, and completion", async () => {
  const result = await runOutdoorActivityWorkflow();
  const types = new Set(result.audit.map((event) => event.type));

  assert.equal(types.has("context.gathered"), true);
  assert.equal(types.has("policy.evaluated"), true);
  assert.equal(types.has("approval.queued"), true);
  assert.equal(types.has("approval.granted"), true);
  assert.equal(types.has("tool.executed"), true);
  assert.equal(types.has("task.completed"), true);
});
