export const AutonomyMode = Object.freeze({
  ASK_ONLY: "ask_only",
  DRAFT_ONLY: "draft_only",
  LOW_RISK_AUTO: "low_risk_auto",
  TRUSTED_AUTO: "trusted_auto"
});

export const Risk = Object.freeze({
  LOW: "low",
  MEDIUM: "medium",
  HIGH: "high"
});

export const Decision = Object.freeze({
  ALLOW: "allow",
  REQUIRE_APPROVAL: "require_approval",
  BLOCK: "block"
});

export const StepStatus = Object.freeze({
  SUCCEEDED: "succeeded",
  AWAITING_APPROVAL: "awaiting_approval",
  BLOCKED: "blocked",
  FAILED: "failed"
});

export function createTask({ id, userId, goal, mode = AutonomyMode.DRAFT_ONLY, now = new Date() }) {
  return {
    id,
    userId,
    goal,
    mode,
    status: "started",
    createdAt: now.toISOString()
  };
}

export function createAuditEvent(type, detail, now = new Date()) {
  return {
    id: `audit_${now.getTime()}_${Math.random().toString(16).slice(2)}`,
    at: now.toISOString(),
    type,
    detail
  };
}
