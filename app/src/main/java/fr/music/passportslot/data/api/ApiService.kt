package fr.music.passportslot.data.api

import fr.music.passportslot.data.model.GeocodingResponse
import fr.music.passportslot.data.model.TokenResponse
import retrofit2.http.*

/**
 * Retrofit interface for the ANTS Rendez-vous Passeport API.
 */
interface AntsApiService {

    @FormUrlEncoded
    @POST("token")
    suspend fun authenticate(
        @Field("username") username: String,
        @Field("password") password: String
    ): TokenResponse

    @GET("searchCity")
    suspend fun searchCity(
        @Header("Authorization") authHeader: String,
        @Query("name") name: String? = null,
        @Query("zip_code") zipCode: String? = null
    ): Any // Raw JSON response

    @GET("getManagedMeetingPoints")
    suspend fun getManagedMeetingPoints(
        @Header("Authorization") authHeader: String
    ): Any

    @GET("searchOfflineMeetingPoints")
    suspend fun searchOfflineMeetingPoints(
        @Header("Authorization") authHeader: String,
        @Query("longitude") longitude: Double,
        @Query("latitude") latitude: Double,
        @Query("radius_km") radiusKm: Int
    ): Any

    @GET("status")
    suspend fun getStatus(
        @Header("Authorization") authHeader: String
    ): Any
}

/**
 * Retrofit interface for the French government geocoding API.
 */
interface GeocodingApiService {

    @GET(".")
    suspend fun searchAddress(
        @Query("q") query: String,
        @Query("type") type: String = "municipality",
        @Query("autocomplete") autocomplete: String = "1"
    ): GeocodingResponse

    @GET(".")
    suspend fun reverseGeocode(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double
    ): GeocodingResponse
}
