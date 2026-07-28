package app.conductor.graph

import app.conductor.runtime.SystemClock
import app.conductor.storage.ConductorRecordStore

/**
 * Seeds purpose-scoped base grants for life-app subagent domains so source
 * authorization does not silently block banking/shopping/contacts/maps/web.
 */
object LifeSourceGrantSeeder {
    fun seedDefaults(recordStore: ConductorRecordStore) {
        val existing = recordStore.graphGrants().map { it.id }.toSet()
        val expires = SystemClock.plusHours(24)
        val defaults = listOf(
            GraphGrant("grant_life_contacts", "device_contacts", "device", setOf("activity_planning", "messaging", "life_ops"), expiresAtIso = expires),
            GraphGrant("grant_life_calendar", "google_calendar", "personal", setOf("activity_planning", "scheduling", "life_ops"), expiresAtIso = expires),
            GraphGrant("grant_life_maps", "maps", "device", setOf("activity_planning", "navigation", "life_ops"), expiresAtIso = expires),
            GraphGrant("grant_life_events", "facebook_events", "personal", setOf("activity_planning", "life_ops"), expiresAtIso = expires),
            GraphGrant("grant_life_weather", "weather_provider", "device", setOf("activity_planning"), expiresAtIso = expires),
            GraphGrant("grant_life_shopping", "shopping", "device", setOf("shopping", "life_ops"), expiresAtIso = expires),
            GraphGrant("grant_life_banking", "banking", "device", setOf("banking", "life_ops"), expiresAtIso = expires),
            GraphGrant("grant_life_web", "web", "device", setOf("web", "life_ops"), expiresAtIso = expires)
        )
        defaults.filter { it.id !in existing }.forEach(recordStore::saveGraphGrant)

        val agentExisting = recordStore.appAgentGrants().map { it.id }.toSet()
        val packages = listOf(
            "com.google.android.apps.messaging" to setOf("device_contacts"),
            "com.google.android.calendar" to setOf("google_calendar"),
            "com.google.android.apps.maps" to setOf("maps"),
            "com.google.android.contacts" to setOf("device_contacts"),
            "com.google.android.gm" to setOf("device_contacts"),
            "com.facebook.katana" to setOf("facebook_events"),
            "com.amazon.mShop.android.shopping" to setOf("shopping"),
            "com.walmart.android" to setOf("shopping"),
            "com.target.ui" to setOf("shopping"),
            "com.chase.sig.android" to setOf("banking"),
            "com.bankofamerica.mobile" to setOf("banking"),
            "com.paypal.android.p2pmobile" to setOf("banking"),
            "com.google.android.apps.walletnfcrel" to setOf("banking"),
            "com.android.chrome" to setOf("web"),
            "app.conductor.prototype" to setOf(
                "device_contacts", "google_calendar", "maps", "facebook_events",
                "weather_provider", "shopping", "banking", "web", "conductor_memory"
            )
        )
        packages.forEach { (pkg, sources) ->
            val id = "agent_grant_life_${pkg.substringAfterLast('.')}"
            if (id !in agentExisting) {
                recordStore.saveAppAgentGrant(
                    AppAgentGrant(
                        id = id,
                        appAgentId = "conductor.voice",
                        packageName = pkg,
                        purposes = setOf("activity_planning", "life_ops", "messaging", "scheduling", "navigation", "shopping", "banking", "web"),
                        sources = sources,
                        expiresAtIso = expires
                    )
                )
            }
        }
    }
}
