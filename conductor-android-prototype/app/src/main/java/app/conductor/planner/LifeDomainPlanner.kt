package app.conductor.planner

import app.conductor.audit.AuditLedger
import app.conductor.operator.accessibility.LifeAppSkillCatalog
import app.conductor.operator.accessibility.LifeDomain
import app.conductor.runtime.Plan
import app.conductor.runtime.PlanStep
import app.conductor.runtime.Recommendation
import app.conductor.runtime.Risk
import app.conductor.runtime.Task

/**
 * Turns life-domain voice intents into multi-step plans that route through app subagents.
 */
class LifeDomainPlanner(private val auditLedger: AuditLedger) {
    fun createPlan(task: Task, domain: LifeDomain, utterance: String): Plan {
        val steps = when (domain) {
            LifeDomain.MESSAGING -> messagingSteps(utterance)
            LifeDomain.CALENDAR -> calendarSteps(utterance)
            LifeDomain.CONTACTS -> contactsSteps(utterance)
            LifeDomain.MAPS -> mapsSteps(utterance)
            LifeDomain.EMAIL -> emailSteps(utterance)
            LifeDomain.SHOPPING -> shoppingSteps(utterance)
            LifeDomain.BANKING -> bankingSteps(utterance)
            LifeDomain.SOCIAL -> socialSteps(utterance)
            LifeDomain.BROWSER ->
                if (utterance.contains("demo", ignoreCase = true) ||
                    utterance.contains("live", ignoreCase = true) && utterance.contains("access", ignoreCase = true)
                ) {
                    demoSteps(utterance)
                } else {
                    browserSteps(utterance)
                }
            LifeDomain.TASKS -> taskSteps(utterance)
            LifeDomain.OTHER -> listOf(
                PlanStep(
                    id = "answer_unknown_life_domain",
                    title = "Summarize request",
                    tool = "assistant.answer",
                    actionType = "answer.generate",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf("utterance" to utterance)
                )
            )
        }

        auditLedger.record(
            "plan.life_domain_created",
            "${domain.id}:steps=${steps.size}:${LifeAppSkillCatalog.displayNameFor(steps.firstOrNull()?.tool.orEmpty())}"
        )

        return Plan(
            id = "plan_${task.id}",
            taskId = task.id,
            goal = task.goal,
            recommendation = Recommendation(
                id = "life_${domain.id}",
                title = domain.title,
                startsAtIso = task.createdAtIso,
                distanceMiles = 0.0,
                score = 90
            ),
            steps = steps
        )
    }

    private fun messagingSteps(utterance: String): List<PlanStep> {
        val body = extractAfter(utterance, listOf("that", "saying", "message", "text", "invite")).ifBlank { utterance }
        val recipient = extractRecipient(utterance)
        val send = utterance.contains("send", ignoreCase = true)
        val draft = PlanStep(
            id = "draft_message",
            title = "Draft message via Messages agent",
            tool = "messages.create_draft",
            actionType = "outbound_message.create_draft",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf("recipient" to recipient, "body" to body)
        )
        return if (send) {
            listOf(
                draft,
                PlanStep(
                    id = "send_message",
                    title = "Send exact message after approval",
                    tool = "messages.send",
                    actionType = "outbound_message.send",
                    risk = Risk.MEDIUM,
                    externalSideEffect = true,
                    input = mapOf("recipient" to recipient, "exactBody" to body)
                )
            )
        } else {
            listOf(draft)
        }
    }

    private fun calendarSteps(utterance: String): List<PlanStep> {
        if (utterance.contains("agenda", ignoreCase = true) ||
            utterance.contains("schedule", ignoreCase = true) ||
            utterance.contains("what's on", ignoreCase = true)
        ) {
            return listOf(
                PlanStep(
                    id = "read_agenda",
                    title = "Open calendar agenda via Calendar agent",
                    tool = "calendar.agenda",
                    actionType = "calendar.agenda.read",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = emptyMap()
                )
            )
        }
        val title = extractAfter(utterance, listOf("called", "titled", "for", "hold")).ifBlank { "Voice calendar hold" }
        return listOf(
            PlanStep(
                id = "create_hold",
                title = "Create tentative calendar hold",
                tool = "calendar.create_hold",
                actionType = "calendar.hold.create",
                risk = Risk.LOW,
                externalSideEffect = false,
                input = mapOf("title" to title, "startsAtIso" to "soon")
            )
        )
    }

    private fun contactsSteps(utterance: String): List<PlanStep> {
        val query = extractRecipient(utterance)
        val call = utterance.contains("call", ignoreCase = true) || utterance.contains("dial", ignoreCase = true)
        val lookup = PlanStep(
            id = "lookup_contact",
            title = "Look up contact via Contacts agent",
            tool = "contacts.lookup",
            actionType = "contacts.lookup",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf("query" to query)
        )
        return if (call) {
            listOf(
                lookup,
                PlanStep(
                    id = "call_contact",
                    title = "Call contact after exact approval",
                    tool = "contacts.call",
                    actionType = "contacts.call",
                    risk = Risk.MEDIUM,
                    externalSideEffect = true,
                    input = mapOf("query" to query, "exactBody" to "Call $query")
                )
            )
        } else {
            listOf(lookup)
        }
    }

    private fun mapsSteps(utterance: String): List<PlanStep> {
        val destination = extractAfter(utterance, listOf("to", "toward", "near", "for")).ifBlank { utterance }
        val route = utterance.contains("direction", ignoreCase = true) ||
            utterance.contains("route", ignoreCase = true) ||
            utterance.contains("navigate", ignoreCase = true) ||
            utterance.contains("drive", ignoreCase = true)
        return if (route) {
            listOf(
                PlanStep(
                    id = "open_route",
                    title = "Open route via Maps agent",
                    tool = "maps.open_route",
                    actionType = "maps.route.open",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf("destination" to destination)
                )
            )
        } else {
            listOf(
                PlanStep(
                    id = "search_place",
                    title = "Search place via Maps agent",
                    tool = "maps.search",
                    actionType = "maps.place.search",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf("query" to destination)
                )
            )
        }
    }

    private fun emailSteps(utterance: String): List<PlanStep> {
        val body = extractAfter(utterance, listOf("that", "saying", "about")).ifBlank { utterance }
        val recipient = extractRecipient(utterance)
        val send = utterance.contains("send", ignoreCase = true)
        val draft = PlanStep(
            id = "draft_email",
            title = "Draft email via Gmail agent",
            tool = "email.create_draft",
            actionType = "email.create_draft",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf("recipient" to recipient, "body" to body)
        )
        return if (send) {
            listOf(
                draft,
                PlanStep(
                    id = "send_email",
                    title = "Send exact email after approval",
                    tool = "email.send",
                    actionType = "email.send",
                    risk = Risk.MEDIUM,
                    externalSideEffect = true,
                    input = mapOf("recipient" to recipient, "exactBody" to body)
                )
            )
        } else {
            listOf(draft)
        }
    }

    private fun shoppingSteps(utterance: String): List<PlanStep> {
        val query = extractAfter(utterance, listOf("for", "buy", "shop", "order", "find")).ifBlank { utterance }
        val purchase = utterance.contains("buy", ignoreCase = true) ||
            utterance.contains("purchase", ignoreCase = true) ||
            utterance.contains("checkout", ignoreCase = true) ||
            utterance.contains("order now", ignoreCase = true)
        val cart = utterance.contains("cart", ignoreCase = true) ||
            utterance.contains("add to cart", ignoreCase = true) ||
            (utterance.contains("add", ignoreCase = true) && utterance.contains("cart", ignoreCase = true))
        val search = PlanStep(
            id = "shop_search",
            title = "Search products via Shopping agent",
            tool = "shopping.search",
            actionType = "shopping.search",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf("query" to query)
        )
        return when {
            purchase -> listOf(
                search,
                PlanStep(
                    id = "shop_purchase",
                    title = "Purchase only with exact approval of order summary",
                    tool = "shopping.purchase",
                    actionType = "purchase.create",
                    risk = Risk.HIGH,
                    externalSideEffect = true,
                    input = mapOf("exactBody" to "Purchase: $query")
                )
            )
            cart -> listOf(
                search,
                PlanStep(
                    id = "shop_cart",
                    title = "Add item to cart without purchase",
                    tool = "shopping.cart",
                    actionType = "shopping.cart.add",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = mapOf("query" to query)
                )
            )
            else -> listOf(search)
        }
    }

    private fun bankingSteps(utterance: String): List<PlanStep> {
        val transfer = utterance.contains("transfer", ignoreCase = true) ||
            utterance.contains("send money", ignoreCase = true) ||
            utterance.contains("pay ", ignoreCase = true) && utterance.contains("bank", ignoreCase = true)
        val paypal = utterance.contains("paypal", ignoreCase = true)
        return when {
            transfer && paypal -> listOf(
                PlanStep(
                    id = "paypal_send",
                    title = "Send PayPal payment after exact approval",
                    tool = "payment.send",
                    actionType = "payment.send",
                    risk = Risk.HIGH,
                    externalSideEffect = true,
                    input = mapOf(
                        "recipient" to extractRecipient(utterance),
                        "exactBody" to utterance
                    )
                )
            )
            transfer -> listOf(
                PlanStep(
                    id = "bank_transfer",
                    title = "Bank transfer after exact approval",
                    tool = "banking.transfer",
                    actionType = "banking.transfer.create",
                    risk = Risk.HIGH,
                    externalSideEffect = true,
                    input = mapOf("exactBody" to utterance)
                )
            )
            else -> listOf(
                PlanStep(
                    id = "bank_balance",
                    title = "Read balances via Banking agent",
                    tool = "banking.balance",
                    actionType = "banking.balance.read",
                    risk = Risk.LOW,
                    externalSideEffect = false,
                    input = emptyMap()
                )
            )
        }
    }

    private fun socialSteps(utterance: String): List<PlanStep> = listOf(
        PlanStep(
            id = "public_post",
            title = "Public post after exact approval",
            tool = "facebook.post",
            actionType = "public_post.create",
            risk = Risk.HIGH,
            externalSideEffect = true,
            input = mapOf("exactBody" to utterance)
        )
    )

    private fun browserSteps(utterance: String): List<PlanStep> = listOf(
        PlanStep(
            id = "browser_open",
            title = "Open browser search via Chrome agent",
            tool = "browser.open",
            actionType = "browser.open",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf("query" to utterance)
        )
    )

    private fun demoSteps(utterance: String): List<PlanStep> = listOf(
        PlanStep(
            id = "live_demo_draft",
            title = "Prove live Accessibility operation on Conductor demo surface",
            tool = "demo.draft",
            actionType = "demo.app.draft",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf(
                "body" to utterance
                    .replace("run live demo", "", ignoreCase = true)
                    .replace("agent demo", "", ignoreCase = true)
                    .trim()
                    .ifBlank { "Hello from live accessibility" }
            )
        )
    )

    private fun taskSteps(utterance: String): List<PlanStep> = listOf(
        PlanStep(
            id = "add_task",
            title = "Add task via taught app agent",
            tool = "app_agent.custom",
            actionType = "tasks.add",
            risk = Risk.LOW,
            externalSideEffect = false,
            input = mapOf("title" to utterance)
        )
    )

    private fun extractRecipient(utterance: String): String {
        val patterns = listOf(
            // Phone numbers for OEM Messages draft (smsto deep link).
            Regex("""(?:to|call|text|message|sms)\s+(\+?\d[\d\-\s]{3,}\d)""", RegexOption.IGNORE_CASE),
            Regex("""(?:to|call|text|message|email|pay)\s+([A-Z][a-zA-Z]+(?:\s+[A-Z][a-zA-Z]+)?)"""),
            Regex("""(?:to|call|text|message|email|pay)\s+([a-zA-Z]+)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(utterance)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (match.isNotBlank() && match.lowercase() !in setOf("the", "my", "a", "an")) return match
        }
        return "contact"
    }

    private fun extractAfter(utterance: String, markers: List<String>): String {
        val lower = utterance.lowercase()
        for (marker in markers) {
            val idx = lower.lastIndexOf(marker)
            if (idx >= 0) {
                return utterance.substring(idx + marker.length).trim().trimStart(':', '-', ',')
            }
        }
        return ""
    }
}
