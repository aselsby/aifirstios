package app.conductor.operator.accessibility

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    fun bringToForeground(
        packageName: String,
        request: AppOperationRequest? = null
    ): AppForegroundLaunchResult
}

class RecordingAppForegroundLauncher(
    private val auditLedger: AuditLedger
) : AppForegroundLauncher {
    override fun bringToForeground(
        packageName: String,
        request: AppOperationRequest?
    ): AppForegroundLaunchResult {
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
    override fun bringToForeground(
        packageName: String,
        request: AppOperationRequest?
    ): AppForegroundLaunchResult {
        val intent = resolveLaunchIntent(packageName, request)
            ?: return AppForegroundLaunchResult(
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
                detail = "launch_started:$packageName:${intent.action ?: "main"}"
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

    private fun resolveLaunchIntent(
        packageName: String,
        request: AppOperationRequest?
    ): Intent? {
        if (packageName == context.packageName) {
            return Intent(context, ConductorAgentDemoActivity::class.java)
        }
        // Google Messages: open compose for recipient so a11y can fill body (G4-OEM).
        if (packageName == "com.google.android.apps.messaging") {
            val recipient = request?.input?.get("recipient").orEmpty().trim()
            val address = when {
                recipient.isNotBlank() && recipient.lowercase() != "contact" -> {
                    val digits = recipient.filter { it.isDigit() || it == '+' }
                    digits.ifBlank { recipient }
                }
                else -> "5550100"
            }
            return Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$address")
                setPackage(packageName)
                // Leave body empty so live set_text is what proves accessibility control.
            }
        }
        // Maps: open search for destination when provided.
        if (packageName == "com.google.android.apps.maps") {
            val destination = request?.input?.get("destination").orEmpty().trim()
            if (destination.isNotBlank()) {
                return Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
                ).apply {
                    setPackage(packageName)
                }
            }
        }
        return context.packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(packageName)
            }.takeIf {
                context.packageManager.queryIntentActivities(it, 0).isNotEmpty()
            }
    }
}
