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
        "location.share",
        "contacts.call",
        "purchase.create",
        "banking.transfer.create",
        "payment.send"
    )

    /** Never automate credentials, security settings, or irreversible account destruction. */
    private val blockedAlways = setOf(
        "account_security.change",
        "data.delete",
        "password.enter",
        "mfa.bypass"
    )

    fun evaluate(step: PlanStep, policy: UserPolicy): PolicyResult {
        if (policy.mode == AutonomyMode.ASK_ONLY && step.actionType != "answer.generate") {
            return PolicyResult(
                decision = PolicyDecision.BLOCK,
                reason = "Ask Only mode blocks external actions."
            )
        }

        if (blockedAlways.contains(step.actionType)) {
            return PolicyResult(
                decision = PolicyDecision.BLOCK,
                reason = "This action is never automated by Conductor (security boundary)."
            )
        }

        // Money movement always requires exact approval regardless of autonomy mode.
        if (step.actionType in setOf("purchase.create", "banking.transfer.create", "payment.send")) {
            return PolicyResult(
                decision = PolicyDecision.REQUIRE_APPROVAL,
                reason = "Money-moving actions always require exact user approval of amount and destination."
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
