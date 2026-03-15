package fr.music.passportslot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Represents a meeting point (mairie) that offers passport/CNI appointments.
 */
@Entity(tableName = "meeting_points")
data class MeetingPoint(
    @PrimaryKey
    val id: String,
    val name: String,
    val city: String,
    @SerializedName("zip_code")
    val zipCode: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("distance_km")
    val distanceKm: Double,
    val editor: String? = null,
    val phone: String? = null,
    @SerializedName("appointment_url")
    val appointmentUrl: String? = null
)

/**
 * A single available time slot at a meeting point.
 */
data class Slot(
    val date: String,       // e.g. "2026-04-15"
    val time: String,       // e.g. "10:30"
    val datetime: String,   // ISO 8601 full datetime
    val meetingPointId: String,
    val meetingPointName: String,
    val city: String,
    val zipCode: String,
    val distanceKm: Double,
    val appointmentUrl: String? = null
)

/**
 * Represents the user's search parameters for slot monitoring.
 */
@Entity(tableName = "search_configs")
data class SearchConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val radiusKm: Int = 10,
    val reason: AppointmentReason = AppointmentReason.PASSPORT,
    val documentsNumber: Int = 1,
    val isActive: Boolean = true,
    val checkIntervalMinutes: Int = 15,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Appointment reason/motif.
 */
enum class AppointmentReason(val apiValue: String, val displayLabel: String) {
    CNI("CNI", "Carte nationale d'identite"),
    PASSPORT("PASSPORT", "Passeport"),
    CNI_PASSPORT("CNI-PASSPORT", "CNI + Passeport")
}

/**
 * WebSocket search request payload.
 */
data class SlotSearchRequest(
    @SerializedName("start_date")
    val startDate: String,
    @SerializedName("end_date")
    val endDate: String,
    val latitude: Double,
    val longitude: Double,
    @SerializedName("radius_km")
    val radiusKm: Int,
    val reason: String,
    @SerializedName("documents_number")
    val documentsNumber: Int,
    val address: String = "",
    val absoluteSearch: String = ""
)

/**
 * Streaming response from the WebSocket.
 */
data class SlotStreamResponse(
    @SerializedName("meeting_point_id")
    val meetingPointId: String? = null,
    @SerializedName("meeting_point_name")
    val meetingPointName: String? = null,
    val city: String? = null,
    @SerializedName("zip_code")
    val zipCode: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("distance_km")
    val distanceKm: Double? = null,
    val editor: String? = null,
    val phone: String? = null,
    @SerializedName("appointment_url")
    val appointmentUrl: String? = null,
    val slots: List<SlotDetail>? = null,
    @SerializedName("li_status")
    val liStatus: String? = null,
    @SerializedName("li_message")
    val liMessage: String? = null
)

data class SlotDetail(
    val date: String? = null,
    val time: String? = null,
    val datetime: String? = null
)

/**
 * Auth token response.
 */
data class TokenResponse(
    @SerializedName("access_token")
    val accessToken: String
)

/**
 * Geocoding result from the Geopf API.
 */
data class GeocodingResponse(
    val features: List<GeoFeature>
)

data class GeoFeature(
    val geometry: GeoGeometry,
    val properties: GeoProperties
)

data class GeoGeometry(
    val coordinates: List<Double> // [longitude, latitude]
)

data class GeoProperties(
    val city: String? = null,
    val postcode: String? = null,
    val label: String? = null,
    val name: String? = null
)

/**
 * Represents a found slot for notification persistence.
 */
@Entity(tableName = "found_slots")
data class FoundSlot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val meetingPointName: String,
    val city: String,
    val zipCode: String,
    val date: String,
    val time: String,
    val distanceKm: Double,
    val appointmentUrl: String?,
    val searchConfigId: Long,
    val foundAt: Long = System.currentTimeMillis(),
    val notified: Boolean = false,
    val dismissed: Boolean = false
)
