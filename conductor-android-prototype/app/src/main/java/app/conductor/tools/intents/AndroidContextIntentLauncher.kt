package app.conductor.tools.intents

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

class AndroidContextIntentLauncher(
    private val context: Context
) : AndroidIntentLauncher {
    override fun launch(plan: AndroidIntentPlan): AndroidIntentLaunchResult {
        val intent = Intent(plan.action)
        plan.dataUri?.let { intent.data = Uri.parse(it) }
        plan.packageName?.let { intent.setPackage(it) }
        plan.extras.forEach { (key, value) -> intent.putExtra(key, value) }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            AndroidIntentLaunchResult(
                status = if (plan.requiresUserFinalTap) {
                    AndroidIntentLaunchStatus.NEEDS_HANDOFF
                } else {
                    AndroidIntentLaunchStatus.LAUNCHED
                },
                planId = plan.id,
                detail = if (plan.requiresUserFinalTap) {
                    "Intent launched and waiting for user final tap."
                } else {
                    "Intent launched."
                },
                verificationMethod = plan.verificationMethod
            )
        } catch (error: ActivityNotFoundException) {
            AndroidIntentLaunchResult(
                status = AndroidIntentLaunchStatus.FAILED,
                planId = plan.id,
                detail = error.message ?: "No activity found for ${plan.action}",
                verificationMethod = plan.verificationMethod
            )
        }
    }
}
