package app.conductor.voice

import app.conductor.operator.accessibility.LifeDomain

/**
 * Maps natural language to life domains so apps can be routed as OS subagents.
 */
class LifeDomainIntentRouter {
    fun route(utterance: String): LifeDomainRoute {
        val n = utterance.lowercase()
        return when {
            bankingKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.BANKING, "life_banking", 0.93)
            shoppingKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.SHOPPING, "life_shopping", 0.92)
            mapsKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.MAPS, "life_maps", 0.91)
            contactsKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.CONTACTS, "life_contacts", 0.9)
            emailKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.EMAIL, "life_email", 0.9)
            messagingKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.MESSAGING, "life_messaging", 0.9)
            calendarKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.CALENDAR, "life_calendar", 0.9)
            socialKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.SOCIAL, "life_social", 0.88)
            browserKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.BROWSER, "life_browser", 0.84)
            taskKeywords.any { n.contains(it) } -> LifeDomainRoute(LifeDomain.TASKS, "life_tasks", 0.88)
            else -> LifeDomainRoute(LifeDomain.OTHER, "general_mobile_intent", 0.5)
        }
    }

    private companion object {
        val bankingKeywords = listOf(
            "balance", "bank", "transfer", "paypal", "venmo", "wallet", "account balance", "send money", "payment"
        )
        val shoppingKeywords = listOf(
            "amazon", "walmart", "target", "shop", "shopping", "buy ", "purchase", "add to cart", "checkout", "order "
        )
        val mapsKeywords = listOf(
            "maps", "directions", "navigate", "drive to", "route to", "where is", "nearby", "take me to"
        )
        val contactsKeywords = listOf("contact", "call ", "dial ", "phone ", "who's number", "look up")
        val emailKeywords = listOf("email", "gmail", "inbox", "compose mail")
        val messagingKeywords = listOf("text ", "message ", "sms", "imessage", "invite ", "whatsapp")
        val calendarKeywords = listOf("calendar", "agenda", "schedule", "hold time", "meeting", "appointment")
        val socialKeywords = listOf("facebook", "post publicly", "tweet", "instagram")
        val browserKeywords = listOf("browser", "chrome", "open website", "browse")
        val taskKeywords = listOf("task", "todo", "to-do", "remind", "reminder")
    }
}

data class LifeDomainRoute(
    val domain: LifeDomain,
    val intentType: String,
    val confidence: Double
)
