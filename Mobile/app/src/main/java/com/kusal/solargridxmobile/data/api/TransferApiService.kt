package com.kusal.solargridxmobile.data.api

import com.kusal.solargridxmobile.data.model.EnergyReservation
import retrofit2.http.*

data class QrTicket(val payload: String, val expiresAt: String)
data class VerifyQr(val payload: String, val sellerNIC: String)
data class MeterReading(val transferredEnergyKWh: Double)
data class EndTransfer(val reason: String)
data class TransferEvent(val action: String, val status: String, val actorNIC: String, val at: String)
data class EnergyTransfer(
    val id: String, val reservationId: String, val status: String,
    val sellerId: String, val buyerId: String,
    val expectedEnergyKWh: Double, val transferredEnergyKWh: Double,
    val verifiedAt: String?, val history: List<TransferEvent>
)
data class BookingPage(val items: List<EnergyReservation>, val total: Int)
data class DashboardSummary(val pending: Int, val current: Int, val approvedFuture: Int, val completed: Int, val cancelled: Int)

interface TransferApiService {
    @GET("Reservation/{id}/qr") suspend fun qr(@Path("id") id: String): QrTicket
    @POST("energy-transfers/verify") suspend fun verify(@Body request: VerifyQr): EnergyTransfer
    @PATCH("energy-transfers/{id}/start") suspend fun start(@Path("id") id: String): EnergyTransfer
    @PATCH("energy-transfers/{id}/progress") suspend fun progress(@Path("id") id: String, @Body request: MeterReading): EnergyTransfer
    @PATCH("energy-transfers/{id}/complete") suspend fun complete(@Path("id") id: String, @Body request: MeterReading): EnergyTransfer
    @PATCH("energy-transfers/{id}/cancel") suspend fun cancel(@Path("id") id: String, @Body request: EndTransfer): EnergyTransfer
    @PATCH("energy-transfers/{id}/fail") suspend fun fail(@Path("id") id: String, @Body request: EndTransfer): EnergyTransfer
    @GET("energy-transfers") suspend fun transfers(@Query("status") status: String? = null, @Query("reservationId") reservationId: String? = null): List<EnergyTransfer>
    @GET("dashboard/summary") suspend fun summary(): DashboardSummary
    @GET("bookings/{view}") suspend fun bookings(@Path("view") view: String, @Query("search") search: String, @Query("page") page: Int): BookingPage
}
