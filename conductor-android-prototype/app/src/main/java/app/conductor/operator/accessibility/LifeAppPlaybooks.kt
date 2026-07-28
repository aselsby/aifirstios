package app.conductor.operator.accessibility

/**
 * Built-in playbooks for the life-management agent surface.
 *
 * These treat major personal apps as OS subagents: messages, calendar, contacts,
 * maps, email, shopping, banking/wallet. Money-moving actions always require
 * exact approval at the playbook + policy layers.
 */
object LifeAppPlaybooks {
    fun all(): List<AppOperationPlaybook> = listOf(
        // --- Contacts ---
        playbook(
            id = "contacts_lookup_person",
            packageName = "com.google.android.contacts",
            actionType = "contacts.lookup",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("find contact", "look up contact", "search contacts", "who is"),
            accountProof = "Contacts signed in",
            inputs = setOf("query"),
            sources = setOf("device_contacts"),
            steps = listOf(
                step("open_search", "Open contacts search.", "search contacts", "search_visible", "set_text", "query", listOf("search")),
                step("open_result", "Open the matching contact card.", "contact result matches input.query", "contact_card_visible")
            )
        ),
        playbook(
            id = "contacts_call_person",
            packageName = "com.google.android.contacts",
            actionType = "contacts.call",
            riskLabel = "medium_external_side_effect",
            requiresExactApproval = true,
            phrases = setOf("call contact", "phone contact", "dial"),
            accountProof = "Contacts signed in",
            inputs = setOf("query", "exactBody"),
            sources = setOf("device_contacts"),
            steps = listOf(
                step("open_result", "Open the matching contact.", "contact result matches input.query", "contact_card_visible", recover = listOf("search contacts")),
                step("tap_call", "Tap call only after exact approval of the recipient.", "call button", "call_started_or_dialer_visible")
            )
        ),

        // --- Email ---
        playbook(
            id = "gmail_draft_email",
            packageName = "com.google.android.gm",
            actionType = "email.create_draft",
            riskLabel = "low_reversible",
            requiresExactApproval = false,
            phrases = setOf("draft email", "compose email", "write email"),
            accountProof = "Gmail signed in",
            inputs = setOf("recipient", "body"),
            sources = setOf("device_contacts"),
            steps = listOf(
                step("compose", "Open compose.", "compose button", "composer_visible", recover = listOf("write", "new email")),
                step("to_field", "Fill recipient.", "to field", "recipient_filled", "set_text", "recipient"),
                step("body_field", "Fill draft body without sending.", "compose body", "body_filled", "set_text", "body")
            )
        ),
        playbook(
            id = "gmail_send_exact_email",
            packageName = "com.google.android.gm",
            actionType = "email.send",
            riskLabel = "medium_external_side_effect",
            requiresExactApproval = true,
            phrases = setOf("send email", "email send"),
            accountProof = "Gmail signed in",
            inputs = setOf("recipient", "exactBody"),
            sources = setOf("device_contacts"),
            steps = listOf(
                step("compose", "Open compose.", "compose button", "composer_visible", recover = listOf("write")),
                step("to_field", "Fill recipient.", "to field", "recipient_filled", "set_text", "recipient"),
                step("body_field", "Fill exact approved body.", "compose body", "body_equals_input.exactBody", "set_text", "exactBody"),
                step("send", "Tap send only after exact approval.", "send button", "sent_receipt_visible")
            )
        ),

        // --- Calendar extras ---
        playbook(
            id = "calendar_view_agenda",
            packageName = "com.google.android.calendar",
            actionType = "calendar.agenda.read",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("what's on my calendar", "show calendar", "agenda", "my schedule"),
            accountProof = "Calendar signed in",
            inputs = setOf(),
            sources = setOf("google_calendar"),
            steps = listOf(
                step("open_agenda", "Open agenda or schedule view.", "agenda", "agenda_visible", recover = listOf("schedule", "day"))
            )
        ),

        // --- Maps extras ---
        playbook(
            id = "maps_search_place",
            packageName = "com.google.android.apps.maps",
            actionType = "maps.place.search",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("search maps", "find place", "nearby", "where is"),
            accountProof = "Maps signed in",
            inputs = setOf("query"),
            sources = setOf("maps"),
            steps = listOf(
                step("search", "Search for a place.", "search box", "place_results_visible", "set_text", "query", listOf("search here"))
            )
        ),
        playbook(
            id = "maps_share_location_blocked_style",
            packageName = "com.google.android.apps.maps",
            actionType = "location.share",
            riskLabel = "high_privacy_external_side_effect",
            requiresExactApproval = true,
            phrases = setOf("share location", "share my location"),
            accountProof = "Maps signed in",
            inputs = setOf("exactBody"),
            sources = setOf("maps"),
            steps = listOf(
                step("share", "Open share location only after exact approval.", "share location", "share_sheet_visible", recover = listOf("share"))
            )
        ),

        // --- Shopping (Amazon) ---
        playbook(
            id = "amazon_search_product",
            packageName = "com.amazon.mShop.android.shopping",
            actionType = "shopping.search",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("search amazon", "shop for", "find product", "amazon search"),
            accountProof = "Amazon signed in",
            inputs = setOf("query"),
            sources = setOf("shopping"),
            steps = listOf(
                step("search", "Search products.", "search bar", "results_visible", "set_text", "query", listOf("search"))
            )
        ),
        playbook(
            id = "amazon_add_to_cart",
            packageName = "com.amazon.mShop.android.shopping",
            actionType = "shopping.cart.add",
            riskLabel = "low_reversible",
            requiresExactApproval = false,
            phrases = setOf("add to cart", "put in cart"),
            accountProof = "Amazon signed in",
            inputs = setOf("query"),
            sources = setOf("shopping"),
            steps = listOf(
                step("search", "Find product.", "search bar", "results_visible", "set_text", "query"),
                step("open", "Open product.", "product result", "product_page_visible"),
                step("add", "Add to cart without purchasing.", "add to cart", "cart_updated_visible")
            )
        ),
        playbook(
            id = "amazon_purchase_exact",
            packageName = "com.amazon.mShop.android.shopping",
            actionType = "purchase.create",
            riskLabel = "high_money_external_side_effect",
            requiresExactApproval = true,
            phrases = setOf("buy now", "purchase", "checkout", "place order"),
            accountProof = "Amazon signed in",
            inputs = setOf("exactBody"),
            sources = setOf("shopping"),
            steps = listOf(
                step("checkout", "Open checkout.", "proceed to checkout", "checkout_visible", recover = listOf("buy now", "checkout")),
                step("confirm", "Confirm purchase only with exact approved order summary.", "place your order", "order_confirmation_visible")
            )
        ),

        // --- Shopping (Walmart / Target generic patterns) ---
        playbook(
            id = "walmart_search_product",
            packageName = "com.walmart.android",
            actionType = "shopping.search",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("walmart search", "search walmart"),
            accountProof = "Walmart signed in",
            inputs = setOf("query"),
            sources = setOf("shopping"),
            steps = listOf(
                step("search", "Search Walmart.", "search", "results_visible", "set_text", "query")
            )
        ),
        playbook(
            id = "target_search_product",
            packageName = "com.target.ui",
            actionType = "shopping.search",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("target search", "search target"),
            accountProof = "Target signed in",
            inputs = setOf("query"),
            sources = setOf("shopping"),
            steps = listOf(
                step("search", "Search Target.", "search", "results_visible", "set_text", "query")
            )
        ),

        // --- Banking / Wallet (read vs money move) ---
        playbook(
            id = "chase_view_balances",
            packageName = "com.chase.sig.android",
            actionType = "banking.balance.read",
            riskLabel = "low_private_read",
            requiresExactApproval = false,
            phrases = setOf("check balance", "bank balance", "account balance", "how much money"),
            accountProof = "Chase signed in",
            inputs = setOf(),
            sources = setOf("banking"),
            steps = listOf(
                step("accounts", "Open accounts overview.", "accounts", "balances_visible", recover = listOf("home", "accounts"))
            )
        ),
        playbook(
            id = "bofa_view_balances",
            packageName = "com.bankofamerica.mobile",
            actionType = "banking.balance.read",
            riskLabel = "low_private_read",
            requiresExactApproval = false,
            phrases = setOf("bofa balance", "bank of america balance"),
            accountProof = "Bank of America signed in",
            inputs = setOf(),
            sources = setOf("banking"),
            steps = listOf(
                step("accounts", "Open accounts overview.", "accounts", "balances_visible", recover = listOf("home"))
            )
        ),
        playbook(
            id = "chase_transfer_exact",
            packageName = "com.chase.sig.android",
            actionType = "banking.transfer.create",
            riskLabel = "high_money_external_side_effect",
            requiresExactApproval = true,
            phrases = setOf("transfer money", "send money bank", "bank transfer"),
            accountProof = "Chase signed in",
            inputs = setOf("exactBody"),
            sources = setOf("banking"),
            steps = listOf(
                step("transfer", "Open transfer flow.", "transfer", "transfer_form_visible", recover = listOf("pay and transfer", "move money")),
                step("confirm", "Confirm transfer only with exact approved amount and destination.", "review", "transfer_review_visible"),
                step("submit", "Submit transfer after exact approval.", "transfer button", "transfer_submitted_visible")
            )
        ),
        playbook(
            id = "paypal_send_exact",
            packageName = "com.paypal.android.p2pmobile",
            actionType = "payment.send",
            riskLabel = "high_money_external_side_effect",
            requiresExactApproval = true,
            phrases = setOf("paypal send", "send paypal", "pay with paypal"),
            accountProof = "PayPal signed in",
            inputs = setOf("exactBody", "recipient"),
            sources = setOf("banking"),
            steps = listOf(
                step("send", "Open send money.", "send", "send_form_visible", recover = listOf("pay", "send money")),
                step("fill", "Fill recipient and amount from exact approval.", "amount", "amount_filled", "set_text", "exactBody"),
                step("submit", "Submit payment after exact approval.", "send button", "payment_sent_visible")
            )
        ),
        playbook(
            id = "wallet_view_only",
            packageName = "com.google.android.apps.walletnfcrel",
            actionType = "wallet.cards.read",
            riskLabel = "low_private_read",
            requiresExactApproval = false,
            phrases = setOf("open wallet", "google wallet", "show cards"),
            accountProof = "Wallet signed in",
            inputs = setOf(),
            sources = setOf("banking"),
            steps = listOf(
                step("home", "Open wallet home without payments.", "wallet", "wallet_home_visible")
            )
        ),

        // --- Browser operator surface for web shopping / banks without native playbooks ---
        playbook(
            id = "chrome_open_url_safe",
            packageName = "com.android.chrome",
            actionType = "browser.open",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            phrases = setOf("open browser", "open chrome", "browse to"),
            accountProof = "Chrome ready",
            inputs = setOf("query"),
            sources = setOf("web"),
            steps = listOf(
                step("omnibox", "Open URL or search without submitting payments.", "search or type url", "page_loaded", "set_text", "query", listOf("address bar", "search"))
            )
        )
    )

    private fun playbook(
        id: String,
        packageName: String,
        actionType: String,
        riskLabel: String,
        requiresExactApproval: Boolean,
        phrases: Set<String>,
        accountProof: String,
        inputs: Set<String>,
        sources: Set<String>,
        steps: List<AppOperationStep>
    ): AppOperationPlaybook = AppOperationPlaybook(
        id = id,
        packageName = packageName,
        actionType = actionType,
        riskLabel = riskLabel,
        requiresExactApproval = requiresExactApproval,
        invocationPhrases = phrases,
        accountProofLabel = accountProof,
        requiredInputKeys = inputs,
        requiredSourceIds = sources,
        steps = steps
    )

    private fun step(
        id: String,
        description: String,
        selector: String,
        expected: String,
        operation: String = "auto",
        inputKey: String = "",
        recover: List<String> = emptyList()
    ): AppOperationStep = AppOperationStep(
        id = id,
        description = description,
        selectorHint = selector,
        expectedState = expected,
        operation = operation,
        inputKey = inputKey,
        recoverySelectorHints = recover
    )
}
