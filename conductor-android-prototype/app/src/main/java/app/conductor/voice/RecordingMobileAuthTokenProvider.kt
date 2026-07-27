package app.conductor.voice

class RecordingMobileAuthTokenProvider(
    private val userId: String = "user_001"
) : MobileAuthTokenProvider {
    override fun bearerToken(): String = "mobile-user:$userId"
}
