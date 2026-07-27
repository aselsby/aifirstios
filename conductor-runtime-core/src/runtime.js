import { AuditLog } from "./audit-log.js";
import { AutonomyMode, createTask } from "./domain.js";
import { ContextBroker } from "./context-broker.js";
import { createMockConnectors } from "./mock-connectors.js";
import { createOutdoorActivityPlan } from "./planner.js";
import { createToolRegistry } from "./tool-registry.js";
import { Executor } from "./executor.js";

export async function runOutdoorActivityWorkflow({
  goal = "Find me something outdoors to do this afternoon and draft an invite to Maya.",
  mode = AutonomyMode.DRAFT_ONLY
} = {}) {
  const auditLog = new AuditLog();
  const task = createTask({
    id: "task_outdoor_activity",
    userId: "user_001",
    goal,
    mode,
    now: new Date("2026-07-27T10:45:00-05:00")
  });

  auditLog.record("task.started", {
    taskId: task.id,
    goal: task.goal,
    mode: task.mode
  });

  const contextBroker = new ContextBroker(createMockConnectors(), auditLog);
  const context = await contextBroker.gather(task, {
    purpose: "activity_planning",
    sources: ["calendar", "weather", "events", "contacts"]
  });

  const plan = createOutdoorActivityPlan(task, context);
  auditLog.record("plan.created", {
    planId: plan.id,
    stepCount: plan.steps.length,
    recommendation: plan.recommendation.title
  });

  const executor = new Executor({
    tools: createToolRegistry(auditLog),
    auditLog,
    userPolicy: { mode }
  });

  const firstPassResults = await executor.runPlan(plan);
  const awaitingApproval = firstPassResults.filter((result) => result.status === "awaiting_approval");
  const approvedResults = [];

  for (const pending of awaitingApproval) {
    const step = plan.steps.find((candidate) => candidate.id === pending.stepId);
    auditLog.record("approval.granted", {
      approvalId: pending.approval.id,
      exactContent: pending.approval.exactContent
    });
    approvedResults.push(await executor.runStep(step, {
      status: "approved",
      approvalId: pending.approval.id,
      exactContent: pending.approval.exactContent
    }));
  }

  auditLog.record("task.completed", {
    taskId: task.id,
    firstPassSucceeded: firstPassResults.filter((result) => result.status === "succeeded").length,
    approvalsRequired: awaitingApproval.length,
    approvedSucceeded: approvedResults.filter((result) => result.status === "succeeded").length
  });

  return {
    task,
    context,
    plan,
    firstPassResults,
    approvedResults,
    audit: auditLog.all()
  };
}
