package app.conductor.voice

object RealtimeTransportPlan {
    const val SESSION_TOKEN_REQUIRED = true
    const val AUDIO_INPUT_FORMAT = "pcm16_or_platform_speech_text"
    const val ASSISTANT_DELTA_STREAM = "assistant.delta"
    const val TOOL_EXECUTION_BOUNDARY = "model_outputs_intents_not_tools"
    const val TOKEN_SCOPE = "voice:intent_handoff"
    const val TOKEN_OWNER = "server_issued_ephemeral_token"

    val requiredEvents = setOf(
        "session.started",
        "session.token.created",
        "input_audio.delta",
        "input_text.final",
        "assistant.delta",
        "assistant.finished",
        "transport.error"
    )
}
