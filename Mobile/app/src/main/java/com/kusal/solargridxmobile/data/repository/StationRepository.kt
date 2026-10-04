package com.kusal.solargridxmobile.data.repository

import com.kusal.solargridxmobile.data.api.ApiClient
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation

class StationRepository {
    private val api = ApiClient.stationService

    suspend fun getAllStations(): Result<List<SolarStation>> {
        return try {
            val response = api.getAllStations()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch stations: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getStationById(id: String): Result<SolarStation> {
        return try {
            val response = api.getStationById(id)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch station details: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getSlotsByStationId(stationId: String): Result<List<EnergySlot>> {
        return try {
            val response = api.getSlotsByStationId(stationId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to fetch slots for station: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
