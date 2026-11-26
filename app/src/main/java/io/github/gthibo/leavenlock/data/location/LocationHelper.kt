package io.github.gthibo.leavenlock.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
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

    /** Returns a short place label (e.g. "Market St") or null if unavailable. */
    suspend fun currentPlaceLabel(): String? = currentParkedPlace()?.label

    /** Returns the current coordinates + a short place label, or null if unavailable. */
    suspend fun currentParkedPlace(): ParkedPlace? {
        if (!hasPermission()) return null
        val location = try {
            suspendCancellableCoroutine<android.location.Location?> { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: SecurityException) {
            null
        } ?: return null

        return ParkedPlace(
            lat = location.latitude,
            lng = location.longitude,
            label = reverseGeocode(location.latitude, location.longitude),
        )
    }

    /** Raw current location (no geocoding) — used for geofence "moved away" confirmation. */
    suspend fun currentLocation(): android.location.Location? {
        if (!hasPermission()) return null
        return try {
            suspendCancellableCoroutine { cont ->
                fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        } catch (_: SecurityException) {
            null
        }
    }

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
