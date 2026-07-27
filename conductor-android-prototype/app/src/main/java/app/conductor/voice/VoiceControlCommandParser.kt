package app.conductor.voice

import app.conductor.runtime.AutonomyMode

data class VoiceControlCommand(
    val kind: String,
    val autonomyMode: AutonomyMode? = null,
    val packageName: String = "",
    val actionType: String = "",
    val requireApproval: Boolean = true,
    val stopAllAutonomy: Boolean = false,
    val cancelAllPending: Boolean = false
)

class VoiceControlCommandParser {
    fun parse(utterance: String): VoiceControlCommand? {
        val normalized = utterance.lowercase()
        val globalMode = globalAutonomyMode(normalized)
        if (globalMode != null) {
            return VoiceControlCommand(
                kind = "set_global_autonomy",
                autonomyMode = globalMode,
                stopAllAutonomy = normalized.contains("stop autonomy") ||
                    normalized.contains("stop all autonomy")
            )
        }

        val approvalOverride = approvalOverride(normalized)
        if (approvalOverride != null) {
            return approvalOverride
        }

        val cancelPending = cancelPendingWork(normalized)
        if (cancelPending != null) {
            return cancelPending
        }

        return null
    }

    private fun globalAutonomyMode(normalized: String): AutonomyMode? =
        when {
            normalized.contains("stop autonomy") || normalized.contains("ask only") ->
                AutonomyMode.ASK_ONLY
            normalized.contains("draft only") ->
                AutonomyMode.DRAFT_ONLY
            normalized.contains("low risk auto") || normalized.contains("low-risk auto") ->
                AutonomyMode.LOW_RISK_AUTO
            normalized.contains("trusted auto") ->
                AutonomyMode.TRUSTED_AUTO
            else -> null
        }

    private fun approvalOverride(normalized: String): VoiceControlCommand? {
        val mentionsApprovalControl =
            normalized.contains("require approval") ||
                normalized.contains("always ask") ||
                normalized.contains("ask before") ||
                normalized.contains("do not ask") ||
                normalized.contains("don't ask") ||
                normalized.contains("no longer require approval") ||
                normalized.contains("allow without approval")
        if (!mentionsApprovalControl) return null

        val requireApproval = !(
            normalized.contains("do not ask") ||
                normalized.contains("don't ask") ||
                normalized.contains("no longer require approval") ||
                normalized.contains("allow without approval")
            )
        val target = actionTarget(normalized) ?: return null
        return VoiceControlCommand(
            kind = "set_action_approval_override",
            packageName = target.packageName,
            actionType = target.actionType,
            requireApproval = requireApproval
        )
    }

    private fun cancelPendingWork(normalized: String): VoiceControlCommand? {
        val wantsCancel =
            normalized.contains("cancel") ||
                normalized.contains("drop") ||
                normalized.contains("discard") ||
                normalized.contains("never mind") ||
                normalized.contains("nevermind")
        val mentionsPendingWork =
            normalized.contains("pending") ||
                normalized.contains("queued") ||
                normalized.contains("handoff") ||
                normalized.contains("app work") ||
                normalized.contains("operation") ||
                normalized.contains("post") ||
                normalized.contains("message") ||
                normalized.contains("send")
        if (!wantsCancel || !mentionsPendingWork) return null

        val target = actionTarget(normalized)
        return VoiceControlCommand(
            kind = "cancel_pending_app_work",
            packageName = target?.packageName.orEmpty(),
            actionType = target?.actionType.orEmpty(),
            cancelAllPending = target == null
        )
    }

    private fun actionTarget(normalized: String): VoiceActionTarget? =
        when {
            normalized.contains("facebook") || normalized.contains("post") || normalized.contains("posting") ->
                VoiceActionTarget(
                    packageName = "com.facebook.katana",
                    actionType = "public_post.create"
                )
            normalized.contains("message") || normalized.contains("send") || normalized.contains("text") ->
                VoiceActionTarget(
                    packageName = "com.google.android.apps.messaging",
                    actionType = "outbound_message.send"
                )
            else -> null
        }
}

private data class VoiceActionTarget(
    val packageName: String,
    val actionType: String
)
