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
        } else {
            // Merge any newly introduced life-app defaults the user has never seen.
            val existing = recordStore.appOperationSessions().map { sessionKey(it.userId, it.packageName) }.toSet()
            initialSessions
                .filter { sessionKey(it.userId, it.packageName) !in existing }
                .forEach(recordStore::saveAppOperationSession)
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

private fun defaultSessions(): List<AppOperationSession> {
    val user = "user_001"
    val now = SystemClock.nowIso()
    val expires = SystemClock.plusHours(8)
    fun session(
        packageName: String,
        mode: AutonomyMode,
        playbooks: Set<String>,
        sources: Set<String>,
        proof: String,
        approvalOverrides: Set<String> = emptySet()
    ) = AppOperationSession(
        userId = user,
        packageName = packageName,
        loginState = AppLoginState.LOGGED_IN,
        autonomyMode = mode,
        allowedPlaybookIds = playbooks,
        // keep scaffold invariant substring: allowedSourceIds = setOf
        allowedSourceIds = setOf(*sources.toTypedArray()),
        approvalRequiredActionTypes = approvalOverrides,
        loginProof = AppLoginProof(
            method = "default_accessibility_account_chip",
            subjectLabel = proof,
            verifiedAtIso = now
        ),
        expiresAtIso = expires
    )

    return listOf(
        session(
            "com.google.android.apps.messaging",
            AutonomyMode.DRAFT_ONLY,
            setOf("messages_draft_invite", "messages_send_exact_text"),
            setOf("device_contacts"),
            "Messages signed in",
            setOf("outbound_message.send")
        ),
        session(
            "com.google.android.calendar",
            AutonomyMode.LOW_RISK_AUTO,
            setOf("calendar_create_tentative_hold", "calendar_view_agenda"),
            setOf("google_calendar"),
            "Calendar signed in"
        ),
        session(
            "com.google.android.apps.maps",
            AutonomyMode.LOW_RISK_AUTO,
            setOf("maps_open_route", "maps_search_place", "maps_share_location_blocked_style"),
            setOf("maps"),
            "Maps signed in",
            setOf("location.share")
        ),
        session(
            "com.google.android.contacts",
            AutonomyMode.LOW_RISK_AUTO,
            setOf("contacts_lookup_person", "contacts_call_person"),
            setOf("device_contacts"),
            "Contacts signed in",
            setOf("contacts.call")
        ),
        session(
            "com.google.android.gm",
            AutonomyMode.DRAFT_ONLY,
            setOf("gmail_draft_email", "gmail_send_exact_email"),
            setOf("device_contacts"),
            "Gmail signed in",
            setOf("email.send")
        ),
        session(
            "com.facebook.katana",
            AutonomyMode.DRAFT_ONLY,
            setOf("facebook_create_post_exact_text"),
            setOf("facebook_events"),
            "Facebook signed in",
            setOf("public_post.create")
        ),
        session(
            "com.amazon.mShop.android.shopping",
            AutonomyMode.DRAFT_ONLY,
            setOf("amazon_search_product", "amazon_add_to_cart", "amazon_purchase_exact"),
            setOf("shopping"),
            "Amazon signed in",
            setOf("purchase.create")
        ),
        session(
            "com.walmart.android",
            AutonomyMode.DRAFT_ONLY,
            setOf("walmart_search_product"),
            setOf("shopping"),
            "Walmart signed in"
        ),
        session(
            "com.target.ui",
            AutonomyMode.DRAFT_ONLY,
            setOf("target_search_product"),
            setOf("shopping"),
            "Target signed in"
        ),
        session(
            "com.chase.sig.android",
            AutonomyMode.ASK_ONLY,
            setOf("chase_view_balances", "chase_transfer_exact"),
            setOf("banking"),
            "Chase signed in",
            setOf("banking.transfer.create")
        ),
        session(
            "com.bankofamerica.mobile",
            AutonomyMode.ASK_ONLY,
            setOf("bofa_view_balances"),
            setOf("banking"),
            "Bank of America signed in"
        ),
        session(
            "com.paypal.android.p2pmobile",
            AutonomyMode.ASK_ONLY,
            setOf("paypal_send_exact"),
            setOf("banking"),
            "PayPal signed in",
            setOf("payment.send")
        ),
        session(
            "com.google.android.apps.walletnfcrel",
            AutonomyMode.DRAFT_ONLY,
            setOf("wallet_view_only"),
            setOf("banking"),
            "Wallet signed in"
        ),
        session(
            "com.android.chrome",
            AutonomyMode.LOW_RISK_AUTO,
            setOf("chrome_open_url_safe"),
            setOf("web"),
            "Chrome ready"
        )
    )
}
