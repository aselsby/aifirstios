package app.conductor.tools.intents

interface AndroidIntentLauncher {
    fun launch(plan: AndroidIntentPlan): AndroidIntentLaunchResult
}
