import { Decision, StepStatus } from "./domain.js";
import { evaluatePolicy } from "./policy-engine.js";

export class Executor {
  constructor({ tools, auditLog, userPolicy }) {
    this.tools = tools;
    this.auditLog = auditLog;
    this.userPolicy = userPolicy;
  }

  async runStep(step, approval = null) {
    const policy = evaluatePolicy(step, this.userPolicy);

    this.auditLog.record("policy.evaluated", {
      stepId: step.id,
      actionType: step.actionType,
      decision: policy.decision,
      reason: policy.reason
    });

    if (policy.decision === Decision.BLOCK) {
      return {
        stepId: step.id,
        status: StepStatus.BLOCKED,
        policy
      };
    }

    const exactContent = step.input.exactBody ?? step.input.body ?? null;

    if (policy.decision === Decision.REQUIRE_APPROVAL && approval?.status !== "approved") {
      const approvalCard = {
        id: `approval_${step.id}`,
        stepId: step.id,
        actionType: step.actionType,
        exactContent,
        recipientContactId: step.input.recipientContactId ?? null,
        reason: policy.reason
      };

      this.auditLog.record("approval.queued", approvalCard);

      return {
        stepId: step.id,
        status: StepStatus.AWAITING_APPROVAL,
        policy,
        approval: approvalCard
      };
    }

    if (
      policy.decision === Decision.REQUIRE_APPROVAL &&
      exactContent !== null &&
      approval.exactContent !== exactContent
    ) {
      this.auditLog.record("approval.rejected", {
        stepId: step.id,
        actionType: step.actionType,
        reason: "exact_content_mismatch"
      });

      return {
        stepId: step.id,
        status: StepStatus.AWAITING_APPROVAL,
        policy,
        approval: {
          id: `approval_${step.id}`,
          stepId: step.id,
          actionType: step.actionType,
          exactContent,
          recipientContactId: step.input.recipientContactId ?? null,
          reason: "Approval must match the exact outbound content."
        }
      };
    }

    const tool = this.tools[step.tool];
    if (!tool) {
      this.auditLog.record("tool.missing", {
        stepId: step.id,
        tool: step.tool
      });

      return {
        stepId: step.id,
        status: StepStatus.FAILED,
        error: `Missing tool: ${step.tool}`
      };
    }

    return tool(step);
  }

  async runPlan(plan) {
    const results = [];

    for (const step of plan.steps) {
      results.push(await this.runStep(step));
    }

    return results;
  }
}
