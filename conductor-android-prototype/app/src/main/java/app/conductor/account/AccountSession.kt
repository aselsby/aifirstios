package app.conductor.account

data class AccountSession(
    val userId: String,
    val displayName: String,
    val bearerToken: String,
    val loggedIn: Boolean,
    val expiresAtIso: String
)

interface AccountSessionStore {
    fun currentSession(): AccountSession?
    fun saveSession(session: AccountSession)
    fun clearSession()
}

class RecordingAccountSessionStore(
    private var session: AccountSession? = defaultAccountSession()
) : AccountSessionStore {
    override fun currentSession(): AccountSession? = session?.takeIf { it.loggedIn }

    override fun saveSession(session: AccountSession) {
        this.session = session
    }

    override fun clearSession() {
        session = null
    }
}

fun defaultAccountSession(): AccountSession =
    AccountSession(
        userId = "user_001",
        displayName = "Alex",
        bearerToken = "mobile-user:user_001",
        loggedIn = true,
        expiresAtIso = "2026-07-27T18:00:00-05:00"
    )
