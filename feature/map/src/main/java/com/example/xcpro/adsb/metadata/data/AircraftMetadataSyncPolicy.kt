package com.example.xcpro.adsb.metadata.data

internal object AircraftMetadataSyncPolicy {
    const val DATABASE_NAME = "adsb_aircraft_metadata.db"
    const val LOOKUP_CHUNK_SIZE = 900
    const val INSERT_BATCH_SIZE = 1000
    const val PERIODIC_SYNC_DAYS = 30L
    const val ONE_TIME_WORK_NAME = "adsb_metadata_initial_sync"
    const val PERIODIC_WORK_NAME = "adsb_metadata_periodic_sync"

    const val SOURCE_BUCKET_LISTING =
        "https://s3.opensky-network.org/data-samples?list-type=2&prefix=metadata/"
    const val SOURCE_BUCKET_OBJECT_BASE = "https://s3.opensky-network.org/data-samples/"
    const val SOURCE_BUCKET_FALLBACK_KEY = "metadata/aircraftDatabase.csv"
    const val SOURCE_DIRECT_FALLBACK =
        "https://opensky-network.org/datasets/metadata/aircraftDatabase.csv"
}

