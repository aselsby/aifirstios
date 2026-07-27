package app.conductor.account

import app.conductor.voice.MobileAuthTokenProvider

class AccountSessionMobileAuthTokenProvider(
    private val accountSessionStore: AccountSessionStore
) : MobileAuthTokenProvider {
    override fun bearerToken(): String? =
        accountSessionStore.currentSession()?.bearerToken
}
