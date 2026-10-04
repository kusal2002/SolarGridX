package com.kusal.solargridxmobile.data.api

import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface StationApiService {
    @GET("stations")
    suspend fun getAllStations(): Response<List<SolarStation>>

    @GET("stations/{id}")
    suspend fun getStationById(@Path("id") id: String): Response<SolarStation>

    @GET("slots/station/{stationId}")
    suspend fun getSlotsByStationId(@Path("stationId") stationId: String): Response<List<EnergySlot>>
}
