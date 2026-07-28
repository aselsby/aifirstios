package app.conductor.operator.accessibility

/**
 * Life domains treated as first-class OS subagent surfaces.
 */
enum class LifeDomain(
    val id: String,
    val title: String,
    val description: String
) {
    MESSAGING("messaging", "Messages", "SMS and chat agents"),
    CALENDAR("calendar", "Calendar", "Schedule and holds"),
    CONTACTS("contacts", "Contacts", "People lookup and call"),
    MAPS("maps", "Maps", "Places and routes"),
    EMAIL("email", "Email", "Draft and send mail"),
    SHOPPING("shopping", "Shopping", "Search, cart, approved purchase"),
    BANKING("banking", "Banking & payments", "Balances, transfers, PayPal"),
    SOCIAL("social", "Social", "Public posts with exact approval"),
    BROWSER("browser", "Browser", "Safe open/search"),
    TASKS("tasks", "Tasks", "Taught app task agents"),
    OTHER("other", "Other apps", "Custom taught agents")
}

data class LifeAppSkill(
    val domain: LifeDomain,
    val packageName: String,
    val displayName: String,
    val actionTypes: List<String>,
    val moneyMoving: Boolean,
    val defaultAutonomy: String
)

object LifeAppSkillCatalog {
    fun domainFor(packageName: String, actionType: String = ""): LifeDomain = when {
        actionType.startsWith("outbound_message") || packageName.contains("messaging") -> LifeDomain.MESSAGING
        actionType.startsWith("calendar") || packageName.contains("calendar") -> LifeDomain.CALENDAR
        actionType.startsWith("contacts") || packageName.contains("contacts") -> LifeDomain.CONTACTS
        actionType.startsWith("maps") || actionType.startsWith("location") || packageName.contains("maps") -> LifeDomain.MAPS
        actionType.startsWith("email") || packageName.endsWith(".gm") -> LifeDomain.EMAIL
        actionType.startsWith("shopping") || actionType.startsWith("purchase") ||
            packageName.contains("amazon") || packageName.contains("walmart") || packageName.contains("target") ->
            LifeDomain.SHOPPING
        actionType.startsWith("banking") || actionType.startsWith("payment") || actionType.startsWith("wallet") ||
            packageName.contains("chase") || packageName.contains("bank") || packageName.contains("paypal") ||
            packageName.contains("wallet") -> LifeDomain.BANKING
        actionType.startsWith("public_post") || packageName.contains("facebook") -> LifeDomain.SOCIAL
        actionType.startsWith("browser") || packageName.contains("chrome") -> LifeDomain.BROWSER
        actionType.startsWith("tasks") -> LifeDomain.TASKS
        else -> LifeDomain.OTHER
    }

    fun moneyMovingAction(actionType: String): Boolean =
        actionType in setOf(
            "purchase.create",
            "banking.transfer.create",
            "payment.send"
        )

    fun skillsFromPlaybooks(playbooks: List<AppOperationPlaybook>): List<LifeAppSkill> =
        playbooks
            .groupBy { it.packageName }
            .map { (packageName, books) ->
                val domain = domainFor(packageName, books.first().actionType)
                LifeAppSkill(
                    domain = domain,
                    packageName = packageName,
                    displayName = displayNameFor(packageName),
                    actionTypes = books.map { it.actionType }.distinct().sorted(),
                    moneyMoving = books.any { moneyMovingAction(it.actionType) || it.requiresExactApproval && it.riskLabel.contains("money") },
                    defaultAutonomy = when (domain) {
                        LifeDomain.BANKING, LifeDomain.SHOPPING, LifeDomain.SOCIAL -> "DRAFT_ONLY"
                        LifeDomain.MAPS, LifeDomain.CALENDAR, LifeDomain.CONTACTS -> "LOW_RISK_AUTO"
                        else -> "DRAFT_ONLY"
                    }
                )
            }
            .sortedWith(compareBy({ it.domain.ordinal }, { it.displayName }))

    fun displayNameFor(packageName: String): String = when (packageName) {
        "com.google.android.apps.messaging" -> "Messages"
        "com.google.android.calendar" -> "Calendar"
        "com.google.android.apps.maps" -> "Maps"
        "com.google.android.contacts" -> "Contacts"
        "com.google.android.gm" -> "Gmail"
        "com.amazon.mShop.android.shopping" -> "Amazon"
        "com.walmart.android" -> "Walmart"
        "com.target.ui" -> "Target"
        "com.chase.sig.android" -> "Chase"
        "com.bankofamerica.mobile" -> "Bank of America"
        "com.paypal.android.p2pmobile" -> "PayPal"
        "com.google.android.apps.walletnfcrel" -> "Google Wallet"
        "com.facebook.katana" -> "Facebook"
        "com.android.chrome" -> "Chrome"
        else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
