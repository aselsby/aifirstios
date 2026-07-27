package app.conductor.graph

import app.conductor.audit.AuditLedger
import app.conductor.runtime.SystemClock

class UserPreferenceMemory(
    private val auditLedger: AuditLedger,
    private val nowIso: () -> String = { SystemClock.nowIso() }
) {
    fun captureActivityPreference(utterance: String, graph: PersonalGraphStore) {
        val preference = extractPreference(utterance) ?: return
        graph.grantAccess(
            GraphGrant(
                id = "grant_conductor_memory_activity",
                source = SOURCE,
                accountId = "conductor_local_memory",
                purposes = setOf(PURPOSE),
                expiresAtIso = "2026-10-27T10:45:00-05:00"
            )
        )
        graph.addFact(
            GraphFact(
                id = "pref_activity_${preference.stableId()}",
                type = "user.preference.activity",
                source = SOURCE,
                accountId = "conductor_local_memory",
                summary = preference,
                redactedSummary = preference,
                sensitivity = Sensitivity.PRIVATE,
                allowedPurposes = setOf(PURPOSE),
                expiresAtIso = "2026-10-27T10:45:00-05:00"
            )
        )
        auditLedger.record("memory.preference_saved", "activity_planning:${preference.take(80)}:${nowIso()}")
    }

    private fun extractPreference(utterance: String): String? {
        val normalized = utterance.trim()
        if (normalized.isBlank()) return null
        val lower = normalized.lowercase()
        val prefix = preferencePrefixes.firstOrNull { lower.contains(it) } ?: return null
        val start = lower.indexOf(prefix) + prefix.length
        return normalized.substring(start)
            .substringBefore(".")
            .substringBefore(";")
            .trim()
            .trimEnd(',')
            .takeIf { it.length >= 4 }
    }

    private fun String.stableId(): String =
        lowercase()
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(48)
            .ifBlank { "activity" }

    private companion object {
        const val SOURCE = "conductor_memory"
        const val PURPOSE = "activity_planning"
        val preferencePrefixes = listOf(
            "remember that i prefer ",
            "remember i prefer ",
            "remember that i like ",
            "remember i like ",
            "i prefer ",
            "i like ",
            "i want ",
            "avoid ",
            "don't suggest ",
            "do not suggest "
        )
    }
}
