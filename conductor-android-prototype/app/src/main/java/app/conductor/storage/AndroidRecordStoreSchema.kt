package app.conductor.storage

object AndroidRecordStoreSchema {
    val createStatements = listOf(
        """
        CREATE TABLE tasks (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            goal TEXT NOT NULL,
            intent_type TEXT NOT NULL DEFAULT 'outdoor_activity',
            autonomy_mode TEXT NOT NULL,
            created_at_iso TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE operation_timelines (
            id TEXT PRIMARY KEY,
            task_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            intent_type TEXT NOT NULL,
            autonomy_mode TEXT NOT NULL,
            started_at_iso TEXT NOT NULL,
            updated_at_iso TEXT NOT NULL,
            events_json TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE graph_grants (
            id TEXT PRIMARY KEY,
            source TEXT NOT NULL,
            account_id TEXT NOT NULL,
            purposes_json TEXT NOT NULL,
            revoked INTEGER NOT NULL DEFAULT 0,
            expires_at_iso TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE app_agent_grants (
            id TEXT PRIMARY KEY,
            app_agent_id TEXT NOT NULL,
            package_name TEXT NOT NULL,
            purposes_json TEXT NOT NULL,
            sources_json TEXT NOT NULL,
            revoked INTEGER NOT NULL DEFAULT 0,
            expires_at_iso TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE graph_facts (
            id TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            source TEXT NOT NULL,
            account_id TEXT NOT NULL,
            summary_ciphertext TEXT NOT NULL,
            redacted_summary TEXT,
            sensitivity TEXT NOT NULL,
            allowed_purposes_json TEXT NOT NULL,
            expires_at_iso TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE approval_decisions (
            id TEXT PRIMARY KEY,
            status TEXT NOT NULL,
            decided_at_iso TEXT NOT NULL,
            action_type TEXT,
            exact_content TEXT
        )
        """.trimIndent(),
        """
        CREATE TABLE consumed_approval_receipts (
            approval_id TEXT PRIMARY KEY,
            action_type TEXT NOT NULL,
            consumed_at_iso TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE autonomy_state (
            id TEXT PRIMARY KEY,
            mode TEXT NOT NULL,
            updated_at_iso TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE account_sessions (
            user_id TEXT PRIMARY KEY,
            display_name TEXT NOT NULL,
            bearer_token_handle TEXT NOT NULL,
            logged_in INTEGER NOT NULL,
            expires_at_iso TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE connector_accounts (
            source TEXT NOT NULL,
            account_id TEXT NOT NULL,
            credential_handle TEXT NOT NULL,
            purposes_json TEXT NOT NULL,
            PRIMARY KEY (source, account_id)
        )
        """.trimIndent(),
        """
        CREATE TABLE app_operation_sessions (
            user_id TEXT NOT NULL,
            package_name TEXT NOT NULL,
            login_state TEXT NOT NULL,
            autonomy_mode TEXT NOT NULL,
            allowed_playbook_ids_json TEXT NOT NULL,
            allowed_source_ids_json TEXT NOT NULL,
            approval_required_action_types_json TEXT NOT NULL DEFAULT '[]',
            remaining_autonomous_actions INTEGER NOT NULL DEFAULT 3,
            login_proof_json TEXT NOT NULL,
            revoked INTEGER NOT NULL DEFAULT 0,
            expires_at_iso TEXT NOT NULL,
            PRIMARY KEY (user_id, package_name)
        )
        """.trimIndent(),
        """
        CREATE TABLE app_operation_playbooks (
            id TEXT PRIMARY KEY,
            package_name TEXT NOT NULL,
            action_type TEXT NOT NULL,
            risk_label TEXT NOT NULL,
            requires_exact_approval INTEGER NOT NULL,
            invocation_phrases_json TEXT NOT NULL DEFAULT '[]',
            account_proof_label TEXT NOT NULL DEFAULT '',
            required_input_keys_json TEXT NOT NULL,
            required_source_ids_json TEXT NOT NULL DEFAULT '[]',
            steps_json TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE app_agent_discoveries (
            package_name TEXT PRIMARY KEY,
            observed_at_iso TEXT NOT NULL,
            visible_label_counts_json TEXT NOT NULL,
            account_proof_candidates_json TEXT NOT NULL,
            bounded INTEGER NOT NULL DEFAULT 1
        )
        """.trimIndent(),
        """
        CREATE TABLE app_operation_queue (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            request_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            package_name TEXT NOT NULL,
            playbook_id TEXT NOT NULL,
            required_source_ids_json TEXT NOT NULL,
            approval_id TEXT,
            approval_action_type TEXT,
            approved_exact_content TEXT,
            input_json TEXT NOT NULL,
            reason TEXT NOT NULL,
            required_user_action TEXT NOT NULL,
            primary_action_label TEXT NOT NULL,
            autonomy_context TEXT NOT NULL,
            created_at_iso TEXT NOT NULL DEFAULT '2026-07-27T10:45:00-05:00',
            expires_at_iso TEXT NOT NULL DEFAULT '2026-07-27T11:15:00-05:00'
        )
        """.trimIndent(),
        """
        CREATE INDEX app_operation_queue_request_id_idx
            ON app_operation_queue (request_id)
        """.trimIndent(),
        """
        CREATE TABLE audit_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL,
            detail TEXT NOT NULL
        )
        """.trimIndent()
    )

    val requiredEncryptedTables = AndroidStoragePlan.encryptedTables
}
