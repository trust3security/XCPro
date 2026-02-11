package com.example.xcpro.di

import com.example.xcpro.airspace.AirspaceUseCase
import com.example.xcpro.flightdata.WaypointFilesUseCase
import com.example.xcpro.replay.IgcReplayController
import com.example.xcpro.tasks.TaskManagerCoordinator
import com.example.xcpro.vario.VarioServiceManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MapUseCaseEntryPoint {
    fun airspaceUseCase(): AirspaceUseCase
    fun waypointFilesUseCase(): WaypointFilesUseCase
    fun taskManagerCoordinator(): TaskManagerCoordinator
    fun varioServiceManager(): VarioServiceManager
    fun igcReplayController(): IgcReplayController
}
