package app.conductor.audit

import android.util.Log
import app.conductor.runtime.AuditEvent
import app.conductor.storage.ConductorRecordStore

class AuditLedger(private val recordStore: ConductorRecordStore? = null) {
    private val events = mutableListOf<AuditEvent>()

    fun record(type: String, detail: String) {
        val event = AuditEvent(type = type, detail = detail)
        events.add(event)
        recordStore?.appendAudit(event)
        if (
            type.startsWith("approval.") ||
            type.startsWith("autonomy.") ||
            type.startsWith("operator.foreground") ||
            type == "operator.queued" ||
            type.startsWith("policy.")
        ) {
            Log.i(TAG, "$type $detail")
        }
    }

    fun all(): List<AuditEvent> = events.toList()

    private companion object {
        const val TAG = "ConductorOS"
    }
}
