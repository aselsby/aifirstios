package app.conductor.storage

object AndroidStoragePlan {
    const val ROOM_DATABASE_NAME = "conductor_records.db"
    const val SQLCIPHER_REQUIRED = true
    const val KEYSTORE_ALIAS = "conductor-record-store-key"

    val encryptedTables = setOf(
        "graph_facts",
        "graph_grants",
        "app_agent_grants",
        "approval_decisions",
        "consumed_approval_receipts",
        "tasks",
        "operation_timelines",
        "audit_events",
        "account_sessions",
        "connector_accounts",
        "app_operation_sessions",
        "app_operation_playbooks",
        "app_agent_discoveries",
        "app_operation_queue"
    )

    val retentionPolicies = mapOf(
        "weather_hourly" to "expire_after_24_hours",
        "calendar_availability" to "expire_after_30_days",
        "facebook_events" to "expire_after_event_end",
        "audit_events" to "retain_until_user_delete",
        "operation_timelines" to "retain_until_user_delete"
    )
}
