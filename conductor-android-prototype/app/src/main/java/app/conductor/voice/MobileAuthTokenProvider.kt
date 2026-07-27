package app.conductor.voice

interface MobileAuthTokenProvider {
    fun bearerToken(): String?
}
