import { AutonomyMode, Decision, Risk } from "./domain.js";

const SENSITIVE_ACTIONS = new Set([
  "outbound_message.send",
  "email.send",
  "public_post.create",
  "calendar.confirm_booking",
  "location.share"
]);

const BLOCKED_IN_MVP = new Set([
  "purchase.create",
  "account_security.change",
  "data.delete"
]);

export function evaluatePolicy(step, userPolicy) {
  const mode = userPolicy?.mode ?? AutonomyMode.DRAFT_ONLY;

  if (mode === AutonomyMode.ASK_ONLY && step.actionType !== "answer.generate") {
    return {
      decision: Decision.BLOCK,
      reason: "Ask Only mode blocks external actions."
    };
  }

  if (BLOCKED_IN_MVP.has(step.actionType)) {
    return {
      decision: Decision.BLOCK,
      reason: "This action is blocked in the MVP until reliability and trust controls are proven."
    };
  }

  if (SENSITIVE_ACTIONS.has(step.actionType)) {
    return {
      decision: Decision.REQUIRE_APPROVAL,
      reason: "Sensitive external actions require exact user approval."
    };
  }

  if (mode === AutonomyMode.DRAFT_ONLY && step.actionType.endsWith(".create_draft")) {
    return {
      decision: Decision.ALLOW,
      reason: "Draft creation has no external side effect."
    };
  }

  if (mode === AutonomyMode.DRAFT_ONLY && step.externalSideEffect === true) {
    return {
      decision: Decision.REQUIRE_APPROVAL,
      reason: "Draft Only mode requires approval before external side effects."
    };
  }

  if (step.risk === Risk.LOW) {
    return {
      decision: Decision.ALLOW,
      reason: "Low-risk context or reversible action."
    };
  }

  return {
    decision: Decision.REQUIRE_APPROVAL,
    reason: "Medium and high-risk steps require user approval."
  };
}
