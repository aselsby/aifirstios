package app.conductor.operator.accessibility

import android.content.Context
import android.content.Intent
import app.conductor.audit.AuditLedger

enum class AppForegroundLaunchStatus {
    LAUNCHED,
    ALREADY_FOREGROUND,
    FAILED
}

data class AppForegroundLaunchResult(
    val status: AppForegroundLaunchStatus,
    val detail: String
)

interface AppForegroundLauncher {
    fun bringToForeground(packageName: String): AppForegroundLaunchResult
}

class RecordingAppForegroundLauncher(
    private val auditLedger: AuditLedger
) : AppForegroundLauncher {
    override fun bringToForeground(packageName: String): AppForegroundLaunchResult {
        auditLedger.record("app_foreground.launch_recorded", packageName)
        return AppForegroundLaunchResult(
            status = AppForegroundLaunchStatus.LAUNCHED,
            detail = "recording_launch:$packageName"
        )
    }
}

class AndroidAppForegroundLauncher(
    private val context: Context,
    private val auditLedger: AuditLedger
) : AppForegroundLauncher {
    override fun bringToForeground(packageName: String): AppForegroundLaunchResult {
        val intent = when {
            packageName == context.packageName ->
                Intent(context, ConductorAgentDemoActivity::class.java)
            else ->
                context.packageManager.getLaunchIntentForPackage(packageName)
        } ?: return AppForegroundLaunchResult(
            status = AppForegroundLaunchStatus.FAILED,
            detail = "launch_intent_missing:$packageName"
        ).also {
            auditLedger.record("app_foreground.launch_failed", it.detail)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            AppForegroundLaunchResult(
                status = AppForegroundLaunchStatus.LAUNCHED,
                detail = "launch_started:$packageName"
            )
        }.getOrElse { error ->
            AppForegroundLaunchResult(
                status = AppForegroundLaunchStatus.FAILED,
                detail = "launch_failed:$packageName:${error.javaClass.simpleName}"
            )
        }.also { result ->
            auditLedger.record("app_foreground.launch_${result.status.name.lowercase()}", result.detail)
        }
    }
}
