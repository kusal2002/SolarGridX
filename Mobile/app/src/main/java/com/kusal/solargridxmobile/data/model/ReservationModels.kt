package com.kusal.solargridxmobile.data.model

import com.google.gson.annotations.SerializedName

data class SolarStation(
    @SerializedName("id") val id: String = "",
    @SerializedName("stationName") val stationName: String = "",
    @SerializedName("location") val location: String = "",
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("totalCapacityKwh") val totalCapacityKwh: Double = 0.0,
    @SerializedName("isActive") val isActive: Boolean = true
)

data class EnergySlot(
    @SerializedName("id") val id: String = "",
    @SerializedName("stationId") val stationId: String = "",
    @SerializedName("slotDate") val slotDate: String = "",
    @SerializedName("startTime") val startTime: String = "",
    @SerializedName("endTime") val endTime: String = "",
    @SerializedName("energyCapacityKwh") val energyCapacityKwh: Double = 0.0,
    @SerializedName("availableEnergyKwh") val availableEnergyKwh: Double = 0.0,
    @SerializedName("isActive") val isActive: Boolean = true
)

data class EnergyReservation(
    @SerializedName("id") val id: String = "",
    @SerializedName("prosumerNIC") val prosumerNIC: String = "",
    @SerializedName("stationId") val stationId: String = "",
    @SerializedName("stationName") val stationName: String? = null,
    @SerializedName("slotId") val slotId: String = "",
    @SerializedName("transferId") val transferId: String? = null,
    @SerializedName("reservationDate") val reservationDate: String = "",
    @SerializedName("startTime") val startTime: String = "",
    @SerializedName("endTime") val endTime: String = "",
    @SerializedName("requestedEnergyKwh") val requestedEnergyKwh: Double = 0.0,
    @SerializedName("status") val status: String = "Pending",
    @SerializedName("cancellationReason") val cancellationReason: String? = null,
    @SerializedName("createdAt") val createdAt: String = "",
    @SerializedName("updatedAt") val updatedAt: String = ""
)

data class CreateReservationRequest(
    @SerializedName("prosumerNIC") val prosumerNIC: String,
    @SerializedName("slotId") val slotId: String,
    @SerializedName("requestedEnergyKwh") val requestedEnergyKwh: Double
)

data class UpdateReservationRequest(
    @SerializedName("newSlotId") val newSlotId: String? = null,
    @SerializedName("requestedEnergyKwh") val requestedEnergyKwh: Double? = null
)
