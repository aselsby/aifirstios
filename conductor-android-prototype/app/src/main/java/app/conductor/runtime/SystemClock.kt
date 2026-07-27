package app.conductor.runtime

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Injectable wall clock for sessions, handoffs, freshness, and audit timestamps.
 *
 * Demo constants remain for scaffold seeds and deterministic static-check fixtures.
 * Production runtime paths must call [nowIso] rather than hard-coding demo values.
 */
object SystemClock {
    /** Deterministic fixture used by seeds, previews, and static invariant checks. */
    const val DEMO_NOW_ISO: String = "2026-07-27T10:45:00-05:00"

    /** Demo handoff expiry (30 minutes after [DEMO_NOW_ISO]). */
    const val DEMO_HANDOFF_EXPIRES_ISO: String = "2026-07-27T11:15:00-05:00"

    /** Demo long-lived session expiry for seeded app-agent grants. */
    const val DEMO_SESSION_EXPIRES_ISO: String = "2026-07-27T18:00:00-05:00"

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @Volatile
    private var provider: () -> String = ::wallClockNowIso

    fun nowIso(): String = provider()

    fun plusMinutes(minutes: Long, fromIso: String = nowIso()): String =
        parse(fromIso).plusMinutes(minutes).format(formatter)

    fun plusHours(hours: Long, fromIso: String = nowIso()): String =
        parse(fromIso).plusHours(hours).format(formatter)

    /** Tests / deterministic demos may freeze time. */
    fun freeze(iso: String = DEMO_NOW_ISO) {
        provider = { iso }
    }

    fun unfreeze() {
        provider = ::wallClockNowIso
    }

    private fun wallClockNowIso(): String =
        OffsetDateTime.now().withNano(0).format(formatter)

    private fun parse(iso: String): OffsetDateTime =
        try {
            OffsetDateTime.parse(iso, formatter)
        } catch (_: Exception) {
            OffsetDateTime.parse(iso)
        }
}
