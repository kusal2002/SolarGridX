package com.kusal.solargridxmobile.data.repository

import com.kusal.solargridxmobile.data.api.ApiClient
import com.kusal.solargridxmobile.data.local.ReservationDbHelper
import com.kusal.solargridxmobile.data.model.CreateReservationRequest
import com.kusal.solargridxmobile.data.model.EnergyReservation
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.data.model.UpdateReservationRequest

class ReservationRepository(
    private val dbHelper: ReservationDbHelper
) {
    private val api = ApiClient.reservationService

    suspend fun getStations(): Result<List<SolarStation>> {
        return try {
            val response = api.getStations()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch stations"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSlots(): Result<List<EnergySlot>> {
        return try {
            val response = api.getSlots()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch slots"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getProsumerReservations(nic: String): Result<List<EnergyReservation>> {
        return try {
            val response = api.getProsumerReservations(nic)
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!
                // Cache into local SQLite database
                dbHelper.saveReservations(list)
                Result.success(list)
            } else {
                // Fallback to SQLite offline cache
                val cached = dbHelper.getReservationsForProsumer(nic)
                if (cached.isNotEmpty()) {
                    Result.success(cached)
                } else {
                    Result.failure(Exception("Failed to fetch reservations"))
                }
            }
        } catch (e: Exception) {
            // Offline fallback to SQLite
            val cached = dbHelper.getReservationsForProsumer(nic)
            if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun createReservation(
        nic: String,
        slotId: String,
        requestedKwh: Double
    ): Result<EnergyReservation> {
        return try {
            val response = api.createReservation(
                CreateReservationRequest(nic, slotId, requestedKwh)
            )
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                dbHelper.saveReservations(listOf(res))
                Result.success(res)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to create reservation"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelReservation(id: String, reason: String?): Result<EnergyReservation> {
        return try {
            val response = api.cancelReservation(id, reason)
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                dbHelper.updateReservationStatus(id, "Cancelled", reason)
                Result.success(res)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to cancel reservation"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun modifyReservation(
        id: String,
        newSlotId: String?,
        requestedKwh: Double?
    ): Result<EnergyReservation> {
        return try {
            val response = api.updateReservation(
                id,
                UpdateReservationRequest(newSlotId, requestedKwh)
            )
            if (response.isSuccessful && response.body() != null) {
                val res = response.body()!!
                dbHelper.saveReservations(listOf(res))
                Result.success(res)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Failed to modify reservation"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
