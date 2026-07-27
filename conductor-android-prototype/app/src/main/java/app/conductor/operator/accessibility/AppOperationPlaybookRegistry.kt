package app.conductor.operator.accessibility

import app.conductor.storage.ConductorRecordStore

class AppOperationPlaybookRegistry(
    customPlaybooks: List<AppOperationPlaybook> = emptyList()
) {
    private val playbooks = builtInPlaybooks() + customPlaybooks

    private fun builtInPlaybooks(): List<AppOperationPlaybook> = listOf(
        AppOperationPlaybook(
            id = "messages_draft_invite",
            packageName = "com.google.android.apps.messaging",
            actionType = "outbound_message.create_draft",
            riskLabel = "low_reversible",
            requiresExactApproval = false,
            invocationPhrases = setOf("draft invite", "draft message", "write message"),
            accountProofLabel = "Messages signed in",
            requiredInputKeys = setOf("recipient", "body"),
            requiredSourceIds = setOf("device_contacts"),
            steps = listOf(
                AppOperationStep(
                    id = "open_recipient_thread",
                    description = "Open the selected contact thread.",
                    selectorHint = "conversation recipient matches input.recipient",
                    expectedState = "recipient_thread_visible",
                    recoverySelectorHints = listOf("new message button", "search conversations")
                ),
                AppOperationStep(
                    id = "fill_draft_body",
                    description = "Fill the compose field without tapping send.",
                    selectorHint = "message input field",
                    expectedState = "compose_text_equals input.body",
                    operation = "set_text",
                    inputKey = "body"
                )
            )
        ),
        AppOperationPlaybook(
            id = "messages_send_exact_text",
            packageName = "com.google.android.apps.messaging",
            actionType = "outbound_message.send",
            riskLabel = "medium_external_side_effect",
            requiresExactApproval = true,
            invocationPhrases = setOf("send message", "text maya", "send invite"),
            accountProofLabel = "Messages signed in",
            requiredInputKeys = setOf("recipient", "exactBody"),
            requiredSourceIds = setOf("device_contacts"),
            steps = listOf(
                AppOperationStep(
                    id = "open_thread",
                    description = "Open the selected contact thread.",
                    selectorHint = "conversation recipient matches input.recipient",
                    expectedState = "recipient_thread_visible",
                    recoverySelectorHints = listOf("new message button", "search conversations")
                ),
                AppOperationStep(
                    id = "fill_exact_body",
                    description = "Fill the compose field with the approved text.",
                    selectorHint = "message input field",
                    expectedState = "compose_text_equals input.exactBody",
                    operation = "set_text",
                    inputKey = "exactBody"
                ),
                AppOperationStep(
                    id = "tap_send",
                    description = "Tap send only after exact text verification.",
                    selectorHint = "send button",
                    expectedState = "sent_receipt_visible"
                )
            )
        ),
        AppOperationPlaybook(
            id = "calendar_create_tentative_hold",
            packageName = "com.google.android.calendar",
            actionType = "calendar.hold.create",
            riskLabel = "low_reversible",
            requiresExactApproval = false,
            invocationPhrases = setOf("calendar hold", "hold time", "add to calendar"),
            accountProofLabel = "Calendar signed in",
            requiredInputKeys = setOf("title", "startsAtIso"),
            requiredSourceIds = setOf("google_calendar"),
            steps = listOf(
                AppOperationStep(
                    id = "open_create_event",
                    description = "Open the event creation surface.",
                    selectorHint = "create event button",
                    expectedState = "event_editor_visible",
                    recoverySelectorHints = listOf("add button", "create button")
                ),
                AppOperationStep(
                    id = "fill_hold",
                    description = "Fill title, time, and tentative state.",
                    selectorHint = "title and time fields",
                    expectedState = "event_fields_match_input",
                    operation = "set_text",
                    inputKey = "title"
                ),
                AppOperationStep(
                    id = "save_hold",
                    description = "Save the tentative calendar hold.",
                    selectorHint = "save button",
                    expectedState = "event_saved_receipt_visible"
                )
            )
        ),
        AppOperationPlaybook(
            id = "maps_open_route",
            packageName = "com.google.android.apps.maps",
            actionType = "maps.route.open",
            riskLabel = "low_no_external_side_effect",
            requiresExactApproval = false,
            invocationPhrases = setOf("route", "directions", "open maps"),
            accountProofLabel = "Maps signed in",
            requiredInputKeys = setOf("destination"),
            requiredSourceIds = setOf("maps"),
            steps = listOf(
                AppOperationStep(
                    id = "search_destination",
                    description = "Search for the chosen destination.",
                    selectorHint = "search box",
                    expectedState = "destination_result_visible",
                    operation = "set_text",
                    inputKey = "destination",
                    recoverySelectorHints = listOf("search here", "explore")
                ),
                AppOperationStep(
                    id = "open_directions",
                    description = "Open directions without sharing location externally.",
                    selectorHint = "directions button",
                    expectedState = "route_preview_visible"
                )
            )
        ),
        AppOperationPlaybook(
            id = "facebook_create_post_exact_text",
            packageName = "com.facebook.katana",
            actionType = "public_post.create",
            riskLabel = "high_public_external_side_effect",
            requiresExactApproval = true,
            invocationPhrases = setOf("post publicly", "share publicly", "facebook post"),
            accountProofLabel = "Facebook signed in",
            requiredInputKeys = setOf("exactBody"),
            requiredSourceIds = setOf("facebook_events"),
            steps = listOf(
                AppOperationStep(
                    id = "open_composer",
                    description = "Open the post composer for the logged-in account.",
                    selectorHint = "facebook composer button",
                    expectedState = "post_composer_visible",
                    recoverySelectorHints = listOf("what's on your mind", "create post")
                ),
                AppOperationStep(
                    id = "fill_exact_post",
                    description = "Fill the composer with the exact approved post body.",
                    selectorHint = "post composer text field",
                    expectedState = "composer_text_equals input.exactBody",
                    operation = "set_text",
                    inputKey = "exactBody"
                ),
                AppOperationStep(
                    id = "tap_post",
                    description = "Tap post only after exact text verification.",
                    selectorHint = "post submit button",
                    expectedState = "posted_receipt_visible"
                )
            )
        )
    )

    fun find(playbookId: String): AppOperationPlaybook? =
        playbooks.firstOrNull { it.id == playbookId }

    fun forAction(actionType: String): AppOperationPlaybook? =
        playbooks.firstOrNull { it.actionType == actionType }

    fun forUtterance(utterance: String): AppOperationPlaybook? {
        val match = matchUtterance(utterance)
        return if (match.isAmbiguous) null else match.matches.firstOrNull()
    }

    fun matchUtterance(utterance: String): AppOperationPlaybookMatch {
        val matches = matchingPlaybooksForUtterance(utterance)
        return AppOperationPlaybookMatch(
            matches = matches,
            isAmbiguous = matches.size > 1
        )
    }

    fun matchingPlaybooksForUtterance(utterance: String): List<AppOperationPlaybook> {
        val normalized = utterance.lowercase()
        return all().filter { playbook ->
            normalized.contains(playbook.actionType.lowercase()) ||
                playbook.invocationPhrases.any { phrase ->
                    phrase.isNotBlank() && normalized.contains(phrase.lowercase())
                }
        }
    }

    fun all(): List<AppOperationPlaybook> =
        playbooks.sortedWith(compareBy<AppOperationPlaybook> { it.packageName }.thenBy { it.actionType })

    fun whitelistedPackages(): Set<String> =
        playbooks.map { it.packageName }.toSet()
}

data class AppOperationPlaybookMatch(
    val matches: List<AppOperationPlaybook>,
    val isAmbiguous: Boolean
)

class RecordBackedAppOperationPlaybookRegistryProvider(
    private val recordStore: ConductorRecordStore
) {
    fun current(): AppOperationPlaybookRegistry =
        AppOperationPlaybookRegistry(
            customPlaybooks = recordStore.appOperationPlaybooks()
        )

    fun whitelistedPackages(): Set<String> =
        current().whitelistedPackages()
}
