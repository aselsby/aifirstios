package app.conductor.connectors

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.runtime.SystemClock

/**
 * Looks up a contact preferred for invites. Only exposes a redacted summary to the graph.
 */
class DeviceContactsConnector(
    private val context: Context?,
    private val preferredNameHint: String = "Maya"
) : ConductorConnector {
    override val source: String = "device_contacts"

    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult {
        val lookup = lookupPreferredContact()
        val expires = SystemClock.plusHours(12)
        return ConnectorResult(
            status = "ok",
            grants = listOf(
                GraphGrant(
                    id = "grant_${source}_${request.accountId}",
                    source = source,
                    accountId = request.accountId,
                    purposes = setOf(request.purpose, "messaging"),
                    expiresAtIso = expires
                )
            ),
            facts = listOf(
                GraphFact(
                    id = "device_contact_preference_${request.accountId}",
                    type = "contact_preference",
                    source = source,
                    accountId = request.accountId,
                    summary = lookup.summary,
                    redactedSummary = lookup.redacted,
                    sensitivity = Sensitivity.PRIVATE,
                    allowedPurposes = setOf("activity_planning", "messaging"),
                    expiresAtIso = expires
                )
            )
        )
    }

    private fun lookupPreferredContact(): ContactSummary {
        val appContext = context?.applicationContext
            ?: return ContactSummary(
                summary = "Maya Chen prefers Messages and is often invited to outdoor events. (scaffold contacts)",
                redacted = "Selected contact prefers Messages."
            )
        val permitted = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (!permitted) {
            return ContactSummary(
                summary = "Contacts permission not granted; using scaffold invite target Maya on Messages.",
                redacted = "Contacts permission missing; scaffold invite target selected."
            )
        }
        return try {
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            )
            val selection = "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ?"
            val args = arrayOf("%$preferredNameHint%")
            appContext.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                projection,
                selection,
                args,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else preferredNameHint
                    return ContactSummary(
                        summary = "$name prefers Messages for invites (device contact match).",
                        redacted = "Selected contact prefers Messages."
                    )
                }
            }
            ContactSummary(
                summary = "No device contact matched '$preferredNameHint'; draft invites will use that name.",
                redacted = "No matching contact; invite name kept as provided."
            )
        } catch (_: Exception) {
            ContactSummary(
                summary = "Contacts read failed; scaffold invite target Maya on Messages.",
                redacted = "Contacts read failed; scaffold invite target used."
            )
        }
    }

    private data class ContactSummary(val summary: String, val redacted: String)
}
