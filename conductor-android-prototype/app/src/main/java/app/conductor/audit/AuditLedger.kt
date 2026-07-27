package app.conductor.audit

import app.conductor.runtime.AuditEvent
import app.conductor.storage.ConductorRecordStore

class AuditLedger(private val recordStore: ConductorRecordStore? = null) {
    private val events = mutableListOf<AuditEvent>()

    fun record(type: String, detail: String) {
        val event = AuditEvent(type = type, detail = detail)
        events.add(event)
        recordStore?.appendAudit(event)
    }

    fun all(): List<AuditEvent> = events.toList()
}
