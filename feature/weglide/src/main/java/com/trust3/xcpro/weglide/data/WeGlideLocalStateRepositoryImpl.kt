package com.trust3.xcpro.weglide.data

import com.trust3.xcpro.weglide.domain.WeGlideAircraft
import com.trust3.xcpro.weglide.domain.WeGlideAircraftMapping
import com.trust3.xcpro.weglide.domain.WeGlideLocalStateRepository
import com.trust3.xcpro.weglide.domain.WeGlideUploadQueueRecord
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class WeGlideLocalStateRepositoryImpl @Inject constructor(
    private val aircraftDao: WeGlideAircraftDao,
    private val aircraftMappingDao: WeGlideAircraftMappingDao,
    private val uploadQueueDao: WeGlideUploadQueueDao
) : WeGlideLocalStateRepository {

    override fun observeAircraft(): Flow<List<WeGlideAircraft>> {
        return aircraftDao.observeAll().map { rows -> rows.map(WeGlideAircraftEntity::toDomain) }
    }

    override fun observeMappings(): Flow<List<WeGlideAircraftMapping>> {
        return aircraftMappingDao.observeAll().map { rows -> rows.map(WeGlideAircraftMappingEntity::toDomain) }
    }

    override fun observeQueue(): Flow<List<WeGlideUploadQueueRecord>> {
        return uploadQueueDao.observeAll().map { rows -> rows.map(WeGlideUploadQueueEntity::toDomain) }
    }

    override suspend fun getMapping(profileId: String): WeGlideAircraftMapping? {
        return aircraftMappingDao.getByProfileId(profileId)?.toDomain()
    }

    override suspend fun getAircraftById(aircraftId: Long): WeGlideAircraft? {
        return aircraftDao.getById(aircraftId)?.toDomain()
    }

    override suspend fun replaceAircraft(aircraft: List<WeGlideAircraft>, updatedAtEpochMs: Long) {
        aircraftDao.replaceAll(
            aircraft.map { item -> item.toEntity(updatedAtEpochMs) }
        )
    }

    override suspend fun saveMapping(
        profileId: String,
        aircraft: WeGlideAircraft,
        updatedAtEpochMs: Long
    ) {
        aircraftMappingDao.upsert(
            WeGlideAircraftMappingEntity(
                localProfileId = profileId,
                weglideAircraftId = aircraft.aircraftId,
                weglideAircraftName = aircraft.name,
                updatedAtEpochMs = updatedAtEpochMs
            )
        )
    }

    override suspend fun clearMapping(profileId: String) {
        aircraftMappingDao.deleteByProfileId(profileId)
    }
}
