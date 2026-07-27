package app.conductor.operator.accessibility

import app.conductor.storage.ConductorRecordStore

object CustomAppPlaybookSeeder {
    fun seedDefaults(recordStore: ConductorRecordStore) {
        if (recordStore.appOperationPlaybooks().isNotEmpty()) return

        recordStore.saveAppOperationPlaybook(
            AppOperationPlaybook(
                id = "generic_notes_append_exact_text",
                packageName = "com.example.notes",
                actionType = "notes.append",
                riskLabel = "low_reversible",
                requiresExactApproval = false,
                accountProofLabel = "Notes signed in",
                requiredInputKeys = setOf("title", "body"),
                steps = listOf(
                    AppOperationStep(
                        id = "open_note",
                        description = "Open the selected note.",
                        selectorHint = "note title matches input.title",
                        expectedState = "note_editor_visible"
                    ),
                    AppOperationStep(
                        id = "append_text",
                        description = "Append the requested text.",
                        selectorHint = "note body field",
                        expectedState = "note_body_contains input.body",
                        operation = "set_text",
                        inputKey = "body"
                    ),
                    AppOperationStep(
                        id = "save_note",
                        description = "Save the edited note.",
                        selectorHint = "save button",
                        expectedState = "note_saved_receipt_visible"
                    )
                )
            )
        )

        recordStore.saveAppOperationPlaybook(
            AppOperationPlaybook(
                id = "community_board_create_post_exact_text",
                packageName = "com.example.community",
                actionType = "public_post.create",
                riskLabel = "high_public_external_side_effect",
                requiresExactApproval = true,
                accountProofLabel = "Community signed in",
                requiredInputKeys = setOf("exactBody"),
                requiredSourceIds = setOf("facebook_events"),
                steps = listOf(
                    AppOperationStep(
                        id = "open_post_composer",
                        description = "Open the community board post composer.",
                        selectorHint = "new post button",
                        expectedState = "community_post_composer_visible"
                    ),
                    AppOperationStep(
                        id = "fill_exact_post",
                        description = "Fill the post composer with the exact approved text.",
                        selectorHint = "community post body field",
                        expectedState = "community_post_body_equals input.exactBody",
                        operation = "set_text",
                        inputKey = "exactBody"
                    ),
                    AppOperationStep(
                        id = "submit_post",
                        description = "Submit only after the exact text is verified.",
                        selectorHint = "submit post button",
                        expectedState = "community_post_receipt_visible"
                    )
                )
            )
        )
    }
}
