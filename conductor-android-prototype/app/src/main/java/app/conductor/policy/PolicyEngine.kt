package app.conductor.policy

import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.PlanStep
import app.conductor.runtime.PolicyDecision
import app.conductor.runtime.PolicyResult
import app.conductor.runtime.Risk
import app.conductor.runtime.UserPolicy

class PolicyEngine {
    private val sensitiveActions = setOf(
        "outbound_message.send",
        "email.send",
        "public_post.create",
        "calendar.confirm_booking",
        "location.share"
    )

    private val blockedInMvp = setOf(
        "purchase.create",
        "account_security.change",
        "data.delete"
    )

    fun evaluate(step: PlanStep, policy: UserPolicy): PolicyResult {
        if (policy.mode == AutonomyMode.ASK_ONLY && step.actionType != "answer.generate") {
            return PolicyResult(
                decision = PolicyDecision.BLOCK,
                reason = "Ask Only mode blocks external actions."
            )
        }

        if (blockedInMvp.contains(step.actionType)) {
            return PolicyResult(
                decision = PolicyDecision.BLOCK,
                reason = "This action is blocked in the MVP until reliability and trust controls are proven."
            )
        }

        if (sensitiveActions.contains(step.actionType)) {
            return PolicyResult(
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "Sensitive external actions require exact user approval."
            )
        }

        if (policy.mode == AutonomyMode.DRAFT_ONLY && step.actionType.endsWith(".create_draft")) {
            return PolicyResult(
                decision = PolicyDecision.ALLOW,
                reason = "Draft creation has no external side effect."
            )
        }

        if (policy.mode == AutonomyMode.DRAFT_ONLY && step.externalSideEffect) {
            return PolicyResult(
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "Draft Only mode requires approval before external side effects."
            )
        }

        if (step.risk == Risk.LOW) {
            return PolicyResult(
                decision = PolicyDecision.ALLOW,
                reason = "Low-risk context or reversible action."
            )
        }

        return PolicyResult(
            decision = PolicyDecision.REQUIRE_APPROVAL,
            reason = "Medium and high-risk steps require user approval."
        )
    }
}
