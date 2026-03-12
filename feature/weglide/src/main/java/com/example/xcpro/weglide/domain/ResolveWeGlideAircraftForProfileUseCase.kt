package com.example.xcpro.weglide.domain

import javax.inject.Inject

class ResolveWeGlideAircraftForProfileUseCase @Inject constructor(
    private val repository: WeGlideAircraftMappingReadRepository
) {
    suspend operator fun invoke(profileId: String): WeGlideAircraftMappingResolution {
        require(profileId.isNotBlank()) { "profileId must not be blank" }

        val mapping = repository.getMapping(profileId)
            ?: return WeGlideAircraftMappingResolution(
                profileId = profileId,
                status = WeGlideAircraftMappingResolution.Status.MAPPING_MISSING
            )

        val aircraft = repository.getAircraftById(mapping.weglideAircraftId)
            ?: return WeGlideAircraftMappingResolution(
                profileId = profileId,
                status = WeGlideAircraftMappingResolution.Status.AIRCRAFT_MISSING,
                mapping = mapping
            )

        return WeGlideAircraftMappingResolution(
            profileId = profileId,
            status = WeGlideAircraftMappingResolution.Status.MAPPED,
            mapping = mapping,
            aircraft = aircraft
        )
    }
}
