package fr.music.passportslot.data.repository

import fr.music.passportslot.data.api.GeocodingApiService
import fr.music.passportslot.data.model.GeoFeature
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for geocoding operations.
 * Uses the French government IGN Geoplateforme API.
 */
@Singleton
class GeocodingRepository @Inject constructor(
    private val geocodingApi: GeocodingApiService
) {
    /**
     * Search for municipalities by name.
     * Returns a list of matching geo features with coordinates.
     */
    suspend fun searchMunicipality(query: String): List<GeoFeature> {
        return try {
            val response = geocodingApi.searchAddress(
                query = query,
                type = "municipality",
                autocomplete = "1"
            )
            response.features
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Search for any address (streets, places, etc.).
     */
    suspend fun searchAddress(query: String): List<GeoFeature> {
        return try {
            val response = geocodingApi.searchAddress(
                query = query,
                type = "housenumber",
                autocomplete = "1"
            )
            response.features
        } catch (e: Exception) {
            // Fallback to municipality search
            searchMunicipality(query)
        }
    }

    /**
     * Reverse geocode coordinates to get city/address info.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): GeoFeature? {
        return try {
            val response = geocodingApi.reverseGeocode(latitude, longitude)
            response.features.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
}
