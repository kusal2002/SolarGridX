package com.kusal.solargridxmobile.data.location

import android.location.Location

/**
 * LocationHelper
 *
 * Provides utilities for validating geographic coordinates and checking if
 * coordinates are within Sri Lanka bounds to prevent showing inaccurate foreign/NLP
 * cached coordinates (e.g. 14,580 km away).
 */
object LocationHelper {

    // Approximate bounding box for Sri Lanka (with slight margin)
    private const val SRI_LANKA_MIN_LAT = 5.8
    private const val SRI_LANKA_MAX_LAT = 10.0
    private const val SRI_LANKA_MIN_LNG = 79.5
    private const val SRI_LANKA_MAX_LNG = 82.0

    /**
     * Checks if given latitude and longitude fall within Sri Lanka territory.
     */
    fun isInSriLanka(lat: Double, lng: Double): Boolean {
        if (lat.isNaN() || lng.isNaN()) return false
        return lat in SRI_LANKA_MIN_LAT..SRI_LANKA_MAX_LAT && lng in SRI_LANKA_MIN_LNG..SRI_LANKA_MAX_LNG
    }

    /**
     * Checks if a Location object contains valid coordinates inside Sri Lanka.
     */
    fun isLocationValidAndPlausible(location: Location?): Boolean {
        if (location == null) return false
        return isInSriLanka(location.latitude, location.longitude)
    }
}
