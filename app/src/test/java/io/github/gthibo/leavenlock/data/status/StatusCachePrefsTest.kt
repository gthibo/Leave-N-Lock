package io.github.gthibo.leavenlock.data.status

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Round-trips [CachedStatus] through the persisted preference keys (no DataStore, no Android). */
class StatusCachePrefsTest {

    @Test
    fun rangeMilesRoundTrips() {
        val p = mutablePreferencesOf()
        val saved = CachedStatus(lockState = "LOCKED", hasStatus = true, evBatteryPercent = 72, rangeMiles = 241)
        StatusCache.write(p, saved)
        assertEquals(241, p[intPreferencesKey("range_mi")])
        val back = StatusCache.read(p)
        assertEquals(saved, back)
        assertEquals(241, back.toVehicleStatus()!!.rangeMiles)
    }

    @Test
    fun missingRangeRoundTripsAsNull() {
        val p = mutablePreferencesOf()
        StatusCache.write(p, CachedStatus(rangeMiles = 100))
        StatusCache.write(p, CachedStatus(rangeMiles = null))
        assertNull(StatusCache.read(p).rangeMiles)
    }

    @Test
    fun legacyKmValueIsReadAsMilesAndDroppedOnWrite() {
        val legacy = intPreferencesKey("range_km")
        val p = mutablePreferencesOf(legacy to 318)
        val read = StatusCache.read(p)
        assertEquals(198, read.rangeMiles) // 318 km ≈ 197.6 mi
        StatusCache.write(p, read)
        assertNull(p[legacy])
        assertEquals(198, StatusCache.read(p).rangeMiles)
    }

    @Test
    fun newMilesKeyWinsOverLegacyKm() {
        val p = mutablePreferencesOf(intPreferencesKey("range_km") to 500, intPreferencesKey("range_mi") to 200)
        assertEquals(200, StatusCache.read(p).rangeMiles)
    }
}
