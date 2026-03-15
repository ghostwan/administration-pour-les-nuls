package fr.music.passportslot.util

/**
 * Constants for the ANTS Rendez-vous Passeport API.
 */
object Constants {
    const val ANTS_API_BASE_URL = "https://api.rendezvouspasseport.ants.gouv.fr/api/"
    const val ANTS_WSS_BASE_URL = "wss://api.rendezvouspasseport.ants.gouv.fr/api/"
    const val ANTS_WEB_URL = "https://rendezvouspasseport.ants.gouv.fr"

    const val GEOCODING_SEARCH_URL = "https://data.geopf.fr/geocodage/search/"
    const val GEOCODING_REVERSE_URL = "https://data.geopf.fr/geocodage/reverse/"

    const val FRONT_AUTH_TOKEN = "9Sx3leIS4Q4wMi8xdSYe"
    const val AES_KEY = "s0hCXLUggPAUwUxsNrEjYg=="
    const val AES_IV = "f2f4e6d6f7z"
    const val AUTH_PASSWORD = "root"

    const val WS_SLOTS_ENDPOINT = "SlotsFromPositionStreaming"

    const val DEFAULT_RADIUS_KM = 10
    const val DEFAULT_CHECK_INTERVAL_MINUTES = 15
    const val DEFAULT_SEARCH_MONTHS_AHEAD = 3
    const val DEFAULT_DOCUMENTS_NUMBER = 1

    // Notification
    const val NOTIFICATION_CHANNEL_ID = "slot_alerts"
    const val NOTIFICATION_CHANNEL_NAME = "Alertes de créneaux"
    const val NOTIFICATION_CHANNEL_DESCRIPTION = "Notifications quand un créneau de rendez-vous est disponible"

    // WorkManager
    const val SLOT_CHECK_WORK_NAME = "slot_check_periodic"
    const val SLOT_CHECK_WORK_TAG = "slot_checker"

    // DataStore
    const val DATASTORE_NAME = "settings"
}
