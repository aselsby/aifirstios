package app.conductor.operator.accessibility

import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore

interface AppOperationSessionStore {
    fun sessionFor(userId: String, packageName: String): AppOperationSession?
    fun saveSession(session: AppOperationSession)
}

class InMemoryAppOperationSessionStore(
    initialSessions: List<AppOperationSession> = defaultSessions()
) : AppOperationSessionStore {
    private val sessions = initialSessions.associateBy { sessionKey(it.userId, it.packageName) }.toMutableMap()

    override fun sessionFor(userId: String, packageName: String): AppOperationSession? =
        sessions[sessionKey(userId, packageName)]

    override fun saveSession(session: AppOperationSession) {
        sessions[sessionKey(session.userId, session.packageName)] = session
    }
}

class RecordBackedAppOperationSessionStore(
    private val recordStore: ConductorRecordStore,
    initialSessions: List<AppOperationSession> = defaultSessions()
) : AppOperationSessionStore {
    init {
        if (recordStore.appOperationSessions().isEmpty()) {
            initialSessions.forEach(recordStore::saveAppOperationSession)
        }
    }

    override fun sessionFor(userId: String, packageName: String): AppOperationSession? =
        recordStore.appOperationSessions().firstOrNull {
            it.userId == userId && it.packageName == packageName
        }

    override fun saveSession(session: AppOperationSession) {
        recordStore.saveAppOperationSession(session)
    }
}

private fun sessionKey(userId: String, packageName: String): String = "$userId:$packageName"

private fun defaultSessions(): List<AppOperationSession> =
    listOf(
        AppOperationSession(
            userId = "user_001",
            packageName = "com.google.android.apps.messaging",
            loginState = AppLoginState.LOGGED_IN,
            autonomyMode = AutonomyMode.DRAFT_ONLY,
            allowedPlaybookIds = setOf("messages_draft_invite", "messages_send_exact_text"),
            allowedSourceIds = setOf("device_contacts"),
            approvalRequiredActionTypes = setOf("outbound_message.send"),
            loginProof = AppLoginProof(
                method = "default_accessibility_account_chip",
                subjectLabel = "Messages signed in",
                verifiedAtIso = SystemClock.nowIso()
            ),
            expiresAtIso = SystemClock.plusHours(8)
        ),
        AppOperationSession(
            userId = "user_001",
            packageName = "com.google.android.calendar",
            loginState = AppLoginState.LOGGED_IN,
            autonomyMode = AutonomyMode.LOW_RISK_AUTO,
            allowedPlaybookIds = setOf("calendar_create_tentative_hold"),
            allowedSourceIds = setOf("google_calendar"),
            loginProof = AppLoginProof(
                method = "default_accessibility_account_chip",
                subjectLabel = "Calendar signed in",
                verifiedAtIso = SystemClock.nowIso()
            ),
            expiresAtIso = SystemClock.plusHours(8)
        ),
        AppOperationSession(
            userId = "user_001",
            packageName = "com.google.android.apps.maps",
            loginState = AppLoginState.LOGGED_IN,
            autonomyMode = AutonomyMode.LOW_RISK_AUTO,
            allowedPlaybookIds = setOf("maps_open_route"),
            allowedSourceIds = setOf("maps"),
            loginProof = AppLoginProof(
                method = "default_accessibility_account_chip",
                subjectLabel = "Maps signed in",
                verifiedAtIso = SystemClock.nowIso()
            ),
            expiresAtIso = SystemClock.plusHours(8)
        ),
        AppOperationSession(
            userId = "user_001",
            packageName = "com.facebook.katana",
            loginState = AppLoginState.LOGGED_IN,
            autonomyMode = AutonomyMode.DRAFT_ONLY,
            allowedPlaybookIds = setOf("facebook_create_post_exact_text"),
            allowedSourceIds = setOf("facebook_events"),
            approvalRequiredActionTypes = setOf("public_post.create"),
            loginProof = AppLoginProof(
                method = "default_accessibility_account_chip",
                subjectLabel = "Facebook signed in",
                verifiedAtIso = SystemClock.nowIso()
            ),
            expiresAtIso = SystemClock.plusHours(8)
        )
    )
