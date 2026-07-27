package app.conductor.operator.accessibility

import app.conductor.audit.AuditLedger

class AppOperationInputRepair(
    private val auditLedger: AuditLedger
) {
    fun repairFromUtterance(
        queued: AppOperationQueueItem,
        playbook: AppOperationPlaybook,
        utterance: String
    ): AppOperationInputRepairResult {
        val missingInputKeys = playbook.requiredInputKeys
            .filter { queued.request.input[it].isNullOrBlank() }
            .toSet()
        if (missingInputKeys.isEmpty()) {
            auditLedger.record("operator.input_repair_skipped", "${queued.request.id}:no_missing_inputs")
            return AppOperationInputRepairResult(
                request = queued.request,
                filledInputKeys = emptySet(),
                missingInputKeys = emptySet()
            )
        }

        val repairedInput = queued.request.input + missingInputKeys.mapNotNull { key ->
            valueFor(key, utterance)?.let { key to it }
        }.toMap()
        val stillMissingInputKeys = playbook.requiredInputKeys
            .filter { repairedInput[it].isNullOrBlank() }
            .toSet()
        val filledInputKeys = missingInputKeys - stillMissingInputKeys
        auditLedger.record(
            "operator.input_repair_attempted",
            "${queued.request.id}:filled=${filledInputKeys.sorted().joinToString()}:missing=${stillMissingInputKeys.sorted().joinToString()}"
        )
        return AppOperationInputRepairResult(
            request = queued.request.copy(input = repairedInput),
            filledInputKeys = filledInputKeys,
            missingInputKeys = stillMissingInputKeys
        )
    }

    fun reviseExactContentFromUtterance(
        queued: AppOperationQueueItem,
        utterance: String
    ): AppOperationInputRepairResult {
        val revisedExactBody = valueFor("exactBody", utterance)
        if (revisedExactBody.isNullOrBlank()) {
            auditLedger.record("operator.exact_content_revision_skipped", "${queued.request.id}:blank_revision")
            return AppOperationInputRepairResult(
                request = queued.request,
                filledInputKeys = emptySet(),
                missingInputKeys = setOf("exactBody")
            )
        }

        val revisedInput = queued.request.input + ("exactBody" to revisedExactBody)
        auditLedger.record(
            "operator.exact_content_revised",
            "${queued.request.id}:exactBody"
        )
        return AppOperationInputRepairResult(
            request = queued.request.copy(
                approvalReceipt = null,
                input = revisedInput
            ),
            filledInputKeys = setOf("exactBody"),
            missingInputKeys = emptySet()
        )
    }

    private fun valueFor(key: String, utterance: String): String? {
        val normalized = utterance.trim()
        if (normalized.isBlank()) return null
        return when (key) {
            "recipient" -> afterAny(normalized, listOf("to ", "with ", "invite "))
            "destination" -> afterAny(normalized, listOf("to ", "for ", "at "))
            "title" -> cleaned(normalized, listOf("the title is ", "title is ", "call it ", "add "))
            "body" -> cleaned(normalized, listOf("the body is ", "body is ", "say ", "write "))
            "exactBody" -> cleaned(
                normalized,
                listOf(
                    "the exact text is ",
                    "exact text is ",
                    "change it to ",
                    "change the text to ",
                    "revise it to ",
                    "make it ",
                    "post ",
                    "send ",
                    "say "
                )
            )
            "startsAtIso" -> normalized.takeIf { it.contains("T") && it.contains(":") }
            else -> cleaned(normalized, listOf("$key is ", "$key:"))
        }?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun afterAny(value: String, prefixes: List<String>): String? {
        val lower = value.lowercase()
        val prefix = prefixes.firstOrNull { lower.contains(it) } ?: return value
        return value.substringAfter(prefix, value)
    }

    private fun cleaned(value: String, prefixes: List<String>): String {
        val lower = value.lowercase()
        val prefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return value
        return value.drop(prefix.length)
    }
}

data class AppOperationInputRepairResult(
    val request: AppOperationRequest,
    val filledInputKeys: Set<String>,
    val missingInputKeys: Set<String>
)
