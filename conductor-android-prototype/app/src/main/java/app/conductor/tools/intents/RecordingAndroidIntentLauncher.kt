package app.conductor.tools.intents

class RecordingAndroidIntentLauncher : AndroidIntentLauncher {
    private val launchedPlans = mutableListOf<AndroidIntentPlan>()

    override fun launch(plan: AndroidIntentPlan): AndroidIntentLaunchResult {
        launchedPlans.add(plan)
        return AndroidIntentLaunchResult(
            status = if (plan.requiresUserFinalTap) {
                AndroidIntentLaunchStatus.NEEDS_HANDOFF
            } else {
                AndroidIntentLaunchStatus.LAUNCHED
            },
            planId = plan.id,
            detail = "Recorded ${plan.action} launch for ${plan.id}.",
            verificationMethod = plan.verificationMethod
        )
    }

    fun all(): List<AndroidIntentPlan> =
        launchedPlans.toList()
}
