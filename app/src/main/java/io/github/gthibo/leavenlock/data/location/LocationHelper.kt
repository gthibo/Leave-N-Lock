package io.github.gthibo.leavenlock.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** A remembered parked position: coordinates + an optional human-friendly label. */
data class ParkedPlace(val lat: Double, val lng: Double, val label: String?)

/** Best-effort current location + a short human label, used to remember where the car parked. */
@Singleton
class LocationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fused = LocationServices.getFusedLocationProviderClient(context)

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** True when the user granted precise location (not just "Approximate"). */
    fun hasPreciseLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns a short place label (e.g. "Market St") or null if unavailable. */
    suspend fun currentPlaceLabel(): String? = currentParkedPlace()?.label

    /**
     * Returns the current coordinates + a short place label, or null if unavailable. Waits up to
     * [PARKED_GPS_WAIT_MS] for a GPS fix, so callers in the lock flow run it off the main path.
     */
    suspend fun currentParkedPlace(): ParkedPlace? {
        val location = accurateFix(PARKED_GPS_WAIT_MS) ?: return null
        return ParkedPlace(
            lat = location.latitude,
            lng = location.longitude,
            label = reverseGeocode(location.latitude, location.longitude),
        )
    }

    /**
     * Raw current location (no geocoding) for the geofence: its centre at car Bluetooth connect
     * and the walk-away polls. Waits at most [gpsWaitMs] for GPS before falling back.
     */
    suspend fun currentLocation(gpsWaitMs: Long = GEOFENCE_GPS_WAIT_MS): Location? = accurateFix(gpsWaitMs)

    /**
     * A fresh fix, as accurate as we can get in time: GPS-grade first ([gpsWaitMs]); if that is
     * missing or worse than [FixRules.GOOD_ACCURACY_METERS], one fresh balanced-power fix
     * (Wi-Fi/cell), then the better of the two by [FixRules.best]. Never a cached fix older than
     * [FixRules.MAX_FIX_AGE_MS]: the old default (up to 60 s) could come from while still driving.
     */
    private suspend fun accurateFix(gpsWaitMs: Long): Location? {
        if (!hasPermission()) return null
        val precise = request(Priority.PRIORITY_HIGH_ACCURACY, gpsWaitMs)
        if (precise != null && FixRules.isGoodEnough(precise.quality())) return precise
        val rough = request(Priority.PRIORITY_BALANCED_POWER_ACCURACY, BALANCED_WAIT_MS)
        return FixRules.best(listOfNotNull(precise, rough).map { it to it.quality() })
    }

    private suspend fun request(priority: Int, waitMs: Long): Location? = try {
        withTimeoutOrNull(waitMs + TIMEOUT_SLACK_MS) {
            suspendCancellableCoroutine { cont ->
                val cancel = CancellationTokenSource()
                cont.invokeOnCancellation { cancel.cancel() }
                val request = CurrentLocationRequest.Builder()
                    .setPriority(priority)
                    .setMaxUpdateAgeMillis(FixRules.MAX_FIX_AGE_MS)
                    .setDurationMillis(waitMs)
                    .build()
                fused.getCurrentLocation(request, cancel.token)
                    .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
        }
    } catch (_: SecurityException) {
        null
    }

    private fun Location.quality() = FixQuality(
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        ageMs = (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000,
    )

    /**
     * Street label for the spot, or null. Bounded by [GEOCODE_TIMEOUT_MS] and never throws: it runs
     * inside the auto-lock evaluation, so it must not hold up a lock.
     */
    private suspend fun reverseGeocode(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            withTimeoutOrNull(GEOCODE_TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        // A lambda would only implement onGeocode; onError's default does nothing,
                        // which left this waiting forever when the lookup failed (e.g. no signal).
                        geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(results: MutableList<android.location.Address>) {
                                if (cont.isActive) cont.resume(results.firstOrNull()?.toLabel())
                            }

                            override fun onError(errorMessage: String?) {
                                if (cont.isActive) cont.resume(null)
                            }
                        })
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(lat, lng, 1)?.firstOrNull()?.toLabel()
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        /** GPS wait for the parked spot (runs beside the lock flow, so it can take a while). */
        const val PARKED_GPS_WAIT_MS = 20_000L
        /** GPS wait for geofence reads (inside the confirm window / a broadcast receiver). */
        const val GEOFENCE_GPS_WAIT_MS = 8_000L
        const val BALANCED_WAIT_MS = 5_000L
        /** Extra time past the request's own duration before we stop waiting for Play services. */
        const val TIMEOUT_SLACK_MS = 2_000L
        const val GEOCODE_TIMEOUT_MS = 10_000L
    }

    private fun android.location.Address.toLabel(): String? {
        // Build a detailed, human-friendly label: "12 Market St, San Francisco".
        val street = listOfNotNull(subThoroughfare, thoroughfare).joinToString(" ").ifBlank { null }
        val area = subLocality ?: locality ?: subAdminArea
        val parts = listOfNotNull(street ?: featureName, area).distinct()
        return parts.joinToString(", ").ifBlank { null }
    }
}
