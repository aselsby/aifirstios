package app.conductor.connectors

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.runtime.SystemClock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reads free/busy style availability from the on-device Calendar provider.
 * Falls back to a mock summary when permission is missing so outdoor planning still demos.
 */
class DeviceCalendarConnector(
    private val context: Context?
) : ConductorConnector {
    override val source: String = "google_calendar"

    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult {
        val summary = readAvailabilitySummary()
        val expires = SystemClock.plusHours(6)
        return ConnectorResult(
            status = "ok",
            grants = listOf(
                GraphGrant(
                    id = "grant_${source}_${request.accountId}",
                    source = source,
                    accountId = request.accountId,
                    purposes = setOf(request.purpose, "scheduling"),
                    expiresAtIso = expires
                )
            ),
            facts = listOf(
                GraphFact(
                    id = "device_calendar_freebusy_${request.accountId}",
                    type = "calendar_availability",
                    source = source,
                    accountId = request.accountId,
                    summary = summary.text,
                    redactedSummary = summary.redacted,
                    sensitivity = Sensitivity.PRIVATE,
                    allowedPurposes = setOf("activity_planning", "scheduling"),
                    expiresAtIso = expires
                )
            )
        )
    }

    private fun readAvailabilitySummary(): AvailabilitySummary {
        val appContext = context?.applicationContext
        if (appContext == null) {
            return AvailabilitySummary(
                text = "Free from 2:30 PM to 5:30 PM; dinner hold at 7:00 PM. (scaffold calendar)",
                redacted = "Free from 2:30 PM to 5:30 PM."
            )
        }
        val permitted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
        if (!permitted) {
            return AvailabilitySummary(
                text = "Calendar permission not granted yet; using provisional free window 2:30 PM to 5:30 PM.",
                redacted = "Calendar permission missing; provisional free window assumed."
            )
        }

        return try {
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val end = now.plusMillis(TimeUnit.HOURS.toMillis(8))
            val projection = arrayOf(
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.ALL_DAY
            )
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            android.content.ContentUris.appendId(builder, now.toEpochMilli())
            android.content.ContentUris.appendId(builder, end.toEpochMilli())
            val events = mutableListOf<BusyBlock>()
            appContext.contentResolver.query(
                builder.build(),
                projection,
                null,
                null,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                val beginIdx = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
                val endIdx = cursor.getColumnIndex(CalendarContract.Instances.END)
                val titleIdx = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
                val allDayIdx = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                while (cursor.moveToNext()) {
                    val allDay = allDayIdx >= 0 && cursor.getInt(allDayIdx) == 1
                    if (allDay) continue
                    val beginMs = if (beginIdx >= 0) cursor.getLong(beginIdx) else continue
                    val endMs = if (endIdx >= 0) cursor.getLong(endIdx) else continue
                    val title = if (titleIdx >= 0) cursor.getString(titleIdx).orEmpty() else "Busy"
                    events += BusyBlock(beginMs, endMs, title.ifBlank { "Busy" })
                }
            }

            if (events.isEmpty()) {
                AvailabilitySummary(
                    text = "No events in the next 8 hours on device calendars; free now through evening.",
                    redacted = "No events in the next 8 hours; free now through evening."
                )
            } else {
                val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
                val lines = events.take(5).map { block ->
                    val start = OffsetDateTime.ofInstant(Instant.ofEpochMilli(block.beginMs), zone).format(fmt)
                    val finish = OffsetDateTime.ofInstant(Instant.ofEpochMilli(block.endMs), zone).format(fmt)
                    "${block.title} $start-$finish"
                }
                val freeWindows = freeWindowsSummary(now.toEpochMilli(), end.toEpochMilli(), events, zone, fmt)
                AvailabilitySummary(
                    text = "Device calendar: $freeWindows Busy blocks: ${lines.joinToString("; ")}.",
                    redacted = "Device calendar free windows: $freeWindows"
                )
            }
        } catch (error: SecurityException) {
            AvailabilitySummary(
                text = "Calendar access denied by system; provisional free window 2:30 PM to 5:30 PM.",
                redacted = "Calendar access denied; provisional free window assumed."
            )
        } catch (_: Exception) {
            AvailabilitySummary(
                text = "Calendar read failed; provisional free window 2:30 PM to 5:30 PM.",
                redacted = "Calendar read failed; provisional free window assumed."
            )
        }
    }

    private fun freeWindowsSummary(
        windowStart: Long,
        windowEnd: Long,
        busy: List<BusyBlock>,
        zone: ZoneId,
        fmt: DateTimeFormatter
    ): String {
        val sorted = busy.sortedBy { it.beginMs }
        var cursor = windowStart
        val free = mutableListOf<String>()
        for (block in sorted) {
            if (block.beginMs > cursor) {
                val start = OffsetDateTime.ofInstant(Instant.ofEpochMilli(cursor), zone).format(fmt)
                val finish = OffsetDateTime.ofInstant(Instant.ofEpochMilli(block.beginMs), zone).format(fmt)
                free += "$start-$finish"
            }
            cursor = maxOf(cursor, block.endMs)
        }
        if (cursor < windowEnd) {
            val start = OffsetDateTime.ofInstant(Instant.ofEpochMilli(cursor), zone).format(fmt)
            val finish = OffsetDateTime.ofInstant(Instant.ofEpochMilli(windowEnd), zone).format(fmt)
            free += "$start-$finish"
        }
        return if (free.isEmpty()) "no free gaps detected" else free.joinToString(", ")
    }

    private data class BusyBlock(val beginMs: Long, val endMs: Long, val title: String)
    private data class AvailabilitySummary(val text: String, val redacted: String)
}
