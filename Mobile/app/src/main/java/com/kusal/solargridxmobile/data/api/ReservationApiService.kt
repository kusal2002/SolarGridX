package com.kusal.solargridxmobile.data.api

import com.kusal.solargridxmobile.data.model.CreateReservationRequest
import com.kusal.solargridxmobile.data.model.EnergyReservation
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.data.model.UpdateReservationRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ReservationApiService {
    @GET("stations")
    suspend fun getStations(): Response<List<SolarStation>>

    @GET("slots")
    suspend fun getSlots(): Response<List<EnergySlot>>

    @GET("reservation/prosumer/{nic}")
    suspend fun getProsumerReservations(@Path("nic") nic: String): Response<List<EnergyReservation>>

    @GET("reservation/{id}")
    suspend fun getReservationById(@Path("id") id: String): Response<EnergyReservation>

    @POST("reservation")
    suspend fun createReservation(@Body request: CreateReservationRequest): Response<EnergyReservation>

    @PUT("reservation/{id}")
    suspend fun updateReservation(
        @Path("id") id: String,
        @Body request: UpdateReservationRequest
    ): Response<EnergyReservation>

    @PATCH("reservation/{id}/cancel")
    suspend fun cancelReservation(
        @Path("id") id: String,
        @Query("reason") reason: String?
    ): Response<EnergyReservation>
}
