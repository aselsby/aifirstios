package app.conductor.connectors

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.conductor.graph.GraphFact
import app.conductor.graph.GraphGrant
import app.conductor.graph.Sensitivity
import app.conductor.runtime.SystemClock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Live outdoor weather via Open-Meteo (no API key).
 * Uses coarse device location when permitted; otherwise a configurable default lat/lon.
 */
class OpenMeteoWeatherConnector(
    private val context: Context?,
    private val defaultLatitude: Double = 30.2672,
    private val defaultLongitude: Double = -97.7431
) : ConductorConnector {
    override val source: String = "weather_provider"

    override fun read(request: ConnectorRequest, credentialHandle: String): ConnectorResult {
        val coords = resolveCoordinates()
        val summary = fetchHourlySummary(coords.first, coords.second)
        val expires = SystemClock.plusHours(2)
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
                    id = "open_meteo_weather_${request.accountId}",
                    type = "weather_hourly",
                    source = source,
                    accountId = request.accountId,
                    summary = summary,
                    sensitivity = Sensitivity.PUBLIC,
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
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            val location = providers.firstNotNullOfOrNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            if (location != null) location.latitude to location.longitude
            else defaultLatitude to defaultLongitude
        } catch (_: Exception) {
            defaultLatitude to defaultLongitude
        }
    }

    private fun fetchHourlySummary(latitude: Double, longitude: Double): String {
        return try {
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                executor.submit<String> {
                    val url = URL(
                        "https://api.open-meteo.com/v1/forecast" +
                            "?latitude=$latitude&longitude=$longitude" +
                            "&hourly=temperature_2m,precipitation_probability,weathercode,windspeed_10m" +
                            "&temperature_unit=fahrenheit&windspeed_unit=mph&timezone=auto&forecast_days=1"
                    )
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
                        readTimeout = TimeUnit.SECONDS.toMillis(8).toInt()
                        requestMethod = "GET"
                    }
                    connection.inputStream.bufferedReader().use { reader ->
                        val body = reader.readText()
                        val json = JSONObject(body)
                        val hourly = json.getJSONObject("hourly")
                        val temps = hourly.getJSONArray("temperature_2m")
                        val precip = hourly.getJSONArray("precipitation_probability")
                        val wind = hourly.getJSONArray("windspeed_10m")
                        val codes = hourly.getJSONArray("weathercode")
                        val times = hourly.getJSONArray("time")
                        val afternoonIndexes = (0 until times.length()).filter { index ->
                            val stamp = times.getString(index)
                            stamp.contains("T13:") || stamp.contains("T14:") ||
                                stamp.contains("T15:") || stamp.contains("T16:") ||
                                stamp.contains("T17:")
                        }
                        val sample = afternoonIndexes.ifEmpty {
                            listOf(0.coerceAtMost((temps.length() - 1).coerceAtLeast(0)))
                        }
                        val avgTemp = sample.map { temps.getDouble(it) }.average()
                        val maxPrecip = sample.maxOf { precip.getInt(it) }
                        val avgWind = sample.map { wind.getDouble(it) }.average()
                        val code = sample.map { codes.getInt(it) }.groupingBy { it }.eachCount()
                            .maxByOrNull { it.value }?.key ?: 0
                        val condition = weatherLabel(code)
                        val outdoorOk = maxPrecip < 50 && avgWind < 25
                        val outdoor = if (outdoorOk) "good outdoor window" else "marginal outdoor conditions"
                        String.format(
                            LocaleAware,
                            "%s this afternoon around %.0f F, precip chance %d%%, wind %.0f mph near %.2f,%.2f; %s.",
                            condition,
                            avgTemp,
                            maxPrecip,
                            avgWind,
                            latitude,
                            longitude,
                            outdoor
                        )
                    }
                }.get(10, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        } catch (_: Exception) {
            "Weather fetch unavailable; assuming clear after 1 PM, 78 F, low wind (fallback)."
        }
    }

    private fun weatherLabel(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2, 3 -> "Partly cloudy"
        45, 48 -> "Foggy"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> "Rain likely"
        71, 73, 75, 77, 85, 86 -> "Snow possible"
        95, 96, 99 -> "Thunderstorm risk"
        else -> "Mixed"
    }

    private companion object {
        // Keep formatting stable without importing java.util.Locale into every call site.
        val LocaleAware: java.util.Locale = java.util.Locale.US
    }
}
