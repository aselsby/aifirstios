package app.conductor.policy

import android.content.Context
import app.conductor.runtime.AutonomyMode
import app.conductor.runtime.UserPolicy
import app.conductor.storage.ConductorRecordStore

class UserPolicyStore(
    context: Context,
    private val recordStore: ConductorRecordStore? = null
) {
    private val prefs = context.getSharedPreferences("conductor_user_policy", Context.MODE_PRIVATE)

    fun load(): UserPolicy {
        val rawMode = prefs.getString(KEY_AUTONOMY_MODE, AutonomyMode.DRAFT_ONLY.name)
            ?: AutonomyMode.DRAFT_ONLY.name
        val mode = recordStore?.autonomyMode()
            ?: AutonomyMode.values().firstOrNull { it.name == rawMode }
            ?: AutonomyMode.DRAFT_ONLY
        return UserPolicy(mode = mode)
    }

    fun saveMode(mode: AutonomyMode): UserPolicy {
        prefs.edit().putString(KEY_AUTONOMY_MODE, mode.name).apply()
        recordStore?.saveAutonomyMode(mode)
        return UserPolicy(mode = mode)
    }

    private companion object {
        const val KEY_AUTONOMY_MODE = "autonomy_mode"
    }
}
