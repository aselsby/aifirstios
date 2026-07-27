package app.conductor.connectors

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.runtime.SystemClock
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Nearby outdoor place/event-style candidates via OpenStreetMap Nominatim.
 * Used as the facebook_events source until a real Facebook Graph session is connected.
 */
class NearbyOutdoorEventsConnector(
    private val context: Context?,
    private val defaultLatitude: Double = 30.2672,
    private val defaultLongitude: Double = -97.7431
) : ConductorConnector {
    override val source: String = "facebook_events"

    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult {
        val coords = resolveCoordinates()
        val summary = fetchNearbyOutdoor(coords.first, coords.second)
        val expires = SystemClock.plusHours(6)
        return ConnectorResult(
            status = "ok",
            grants = listOf(
                GraphGrant(
                    id = "grant_${source}_${request.accountId}",
                    source = source,
                    accountId = request.accountId,
                    purposes = setOf(request.purpose),
                    expiresAtIso = expires
                )
            ),
            facts = listOf(
                GraphFact(
                    id = "nearby_outdoor_candidates_${request.accountId}",
                    type = "event_candidate",
                    source = source,
                    accountId = request.accountId,
                    summary = summary,
                    sensitivity = Sensitivity.PERSONAL,
                    allowedPurposes = setOf("activity_planning"),
                    expiresAtIso = expires
                )
            )
        )
    }

    private fun resolveCoordinates(): Pair<Double, Double> {
        val appContext = context?.applicationContext ?: return defaultLatitude to defaultLongitude
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return defaultLatitude to defaultLongitude
        return try {
            val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).firstNotNullOfOrNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            if (location != null) location.latitude to location.longitude
            else defaultLatitude to defaultLongitude
        } catch (_: Exception) {
            defaultLatitude to defaultLongitude
        }
    }

    private fun fetchNearbyOutdoor(latitude: Double, longitude: Double): String {
        return try {
            val executor = Executors.newSingleThreadExecutor()
            try {
                executor.submit<String> {
                    val query = URLEncoder.encode("park", "UTF-8")
                    val url = URL(
                        "https://nominatim.openstreetmap.org/search" +
                            "?q=$query&format=json&limit=5" +
                            "&viewbox=${longitude - 0.15},${latitude + 0.15},${longitude + 0.15},${latitude - 0.15}" +
                            "&bounded=1"
                    )
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
                        readTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "ConductorOS/0.1 (ai-first mobile os prototype)")
                    }
                    connection.inputStream.bufferedReader().use { reader ->
                        val body = reader.readText()
                        val arr = JSONArray(body)
                        if (arr.length() == 0) {
                            return@submit "No nearby outdoor parks found; try Outdoor Jazz At The Garden at 3:30 PM, 2.4 miles (scaffold fallback)."
                        }
                        val picks = (0 until minOf(3, arr.length())).map { index ->
                            val item = arr.getJSONObject(index)
                            val name = item.optString("display_name").split(",").firstOrNull()?.trim().orEmpty()
                                .ifBlank { "Outdoor spot ${index + 1}" }
                            val lat = item.optDouble("lat", latitude)
                            val lon = item.optDouble("lon", longitude)
                            val miles = haversineMiles(latitude, longitude, lat, lon)
                            "$name (~${"%.1f".format(miles)} mi)"
                        }
                        "Nearby outdoor options: ${picks.joinToString("; ")}. Prefer free outdoor parks or gardens this afternoon."
                    }
                }.get(10, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        } catch (_: Exception) {
            "Outdoor Jazz At The Garden at 3:30 PM, 2.4 miles away, free. Nearby farmers market 4:00 PM, 1.2 miles. (events network fallback)"
        }
    }

    private fun haversineMiles(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 3958.8
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
