package app.conductor.account

import app.conductor.storage.ConductorRecordStore

class RecordBackedAccountSessionStore(
    private val recordStore: ConductorRecordStore,
    defaultSession: AccountSession = defaultAccountSession()
) : AccountSessionStore {
    init {
        if (recordStore.accountSessions().isEmpty()) {
            recordStore.saveAccountSession(defaultSession)
        }
    }

    override fun currentSession(): AccountSession? =
        recordStore.accountSessions().lastOrNull { it.loggedIn }

    override fun saveSession(session: AccountSession) {
        recordStore.saveAccountSession(session)
    }

    override fun clearSession() {
        currentSession()?.let { session ->
            recordStore.saveAccountSession(session.copy(loggedIn = false))
        }
    }
}
