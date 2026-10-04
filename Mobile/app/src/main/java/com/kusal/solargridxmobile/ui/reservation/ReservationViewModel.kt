package com.kusal.solargridxmobile.ui.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kusal.solargridxmobile.data.model.EnergyReservation
import com.kusal.solargridxmobile.data.model.EnergySlot
import com.kusal.solargridxmobile.data.model.SolarStation
import com.kusal.solargridxmobile.data.repository.ReservationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class ReservationUiState(
    val isLoading: Boolean = false,
    val stations: List<SolarStation> = emptyList(),
    val slots: List<EnergySlot> = emptyList(),
    val myReservations: List<EnergyReservation> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
) {
    val totalCount: Int get() = myReservations.size
    val pendingCount: Int get() = myReservations.count { it.status.equals("Pending", ignoreCase = true) }
    val approvedCount: Int get() = myReservations.count { it.status.equals("Approved", ignoreCase = true) }
    val completedCount: Int get() = myReservations.count { it.status.equals("Completed", ignoreCase = true) }
    val cancelledCount: Int get() = myReservations.count { it.status.equals("Cancelled", ignoreCase = true) }
    val totalKwh: Double get() = myReservations.filter { it.status != "Cancelled" }.sumOf { it.requestedEnergyKwh }

    val nextUpcomingReservation: EnergyReservation?
        get() {
            val active = myReservations.filter {
                it.status.equals("Pending", ignoreCase = true) || it.status.equals("Approved", ignoreCase = true)
            }
            return active.minByOrNull { it.reservationDate + " " + it.startTime }
        }

    val availableActiveSlots: List<EnergySlot>
        get() {
            val now = System.currentTimeMillis()
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val dateSdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            return slots.filter { slot ->
                if (!slot.isActive || slot.availableEnergyKwh <= 0.0) return@filter false
                try {
                    val datePart = slot.slotDate.split("T")[0].trim()
                    val slotDateObj = dateSdf.parse(datePart) ?: return@filter false
                    val diffDays = (slotDateObj.time - now) / (1000.0 * 60 * 60 * 24)
                    if (diffDays > 7.0) return@filter false

                    val timeSource = if (slot.endTime.isNotBlank()) slot.endTime.trim() else slot.startTime.trim()
                    val timeParts = timeSource.split(":")
                    val hour = timeParts.getOrNull(0)?.padStart(2, '0') ?: "00"
                    val minute = timeParts.getOrNull(1)?.padStart(2, '0') ?: "00"
                    val second = timeParts.getOrNull(2)?.padStart(2, '0') ?: "00"
                    val slotDateTime = sdf.parse("$datePart $hour:$minute:$second")
                    (slotDateTime?.time ?: Long.MAX_VALUE) > now
                } catch (e: Exception) {
                    true
                }
            }
        }
}

class ReservationViewModel(
    private val repository: ReservationRepository,
    private val prosumerNic: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReservationUiState())
    val uiState: StateFlow<ReservationUiState> = _uiState.asStateFlow()

    init {
        loadAllData()
    }

    fun loadAllData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val stationsRes = repository.getStations()
            val slotsRes = repository.getSlots()
            val myRes = repository.getProsumerReservations(prosumerNic)

            val error = stationsRes.exceptionOrNull()?.message
                ?: slotsRes.exceptionOrNull()?.message
                ?: myRes.exceptionOrNull()?.message

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                stations = stationsRes.getOrDefault(emptyList()),
                slots = slotsRes.getOrDefault(emptyList()),
                myReservations = myRes.getOrDefault(emptyList()),
                errorMessage = error
            )
        }
    }

    fun createReservation(slotId: String, requestedKwh: Double, onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.createReservation(prosumerNic, slotId, requestedKwh)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Reservation booked successfully!"
                )
                loadAllData()
                onComplete()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    errorMessage = it.message ?: "Failed to book reservation"
                )
            }
        }
    }

    fun cancelReservation(id: String, reason: String?, onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.cancelReservation(id, reason)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Reservation cancelled successfully"
                )
                loadAllData()
                onComplete()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    errorMessage = it.message ?: "Failed to cancel reservation"
                )
            }
        }
    }

    fun modifyReservation(
        id: String,
        newSlotId: String?,
        requestedKwh: Double?,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            val result = repository.modifyReservation(id, newSlotId, requestedKwh)
            _uiState.value = _uiState.value.copy(isLoading = false)

            result.onSuccess {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Reservation modified successfully"
                )
                loadAllData()
                onComplete()
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    errorMessage = it.message ?: "Failed to modify reservation"
                )
            }
        }
    }

    // Helper: checks 12-hour rule (Lithira - Member 3 requirement)
    fun isEligibleFor12HourRule(reservation: EnergyReservation): Pair<Boolean, Double> {
        return try {
            val dateStr = reservation.reservationDate.split("T")[0]
            val timeParts = reservation.startTime.split(":")
            val hour = timeParts.getOrNull(0)?.padStart(2, '0') ?: "00"
            val minute = timeParts.getOrNull(1)?.padStart(2, '0') ?: "00"
            val timeStr = "$hour:$minute"

            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val startDateTime = format.parse("$dateStr $timeStr")
            val diffMs = (startDateTime?.time ?: 0) - System.currentTimeMillis()
            val hoursRemaining = diffMs / (1000.0 * 60 * 60)
            Pair(hoursRemaining >= 12.0, hoursRemaining)
        } catch (e: Exception) {
            Pair(true, 24.0)
        }
    }

    // Helper: checks if slot is expired (past date or past end-time)
    fun isSlotExpired(slot: EnergySlot): Boolean {
        return try {
            val datePart = slot.slotDate.split("T")[0].trim()
            val timeSource = if (slot.endTime.isNotBlank()) slot.endTime.trim() else slot.startTime.trim()
            val timeParts = timeSource.split(":")
            val hour = timeParts.getOrNull(0)?.padStart(2, '0') ?: "00"
            val minute = timeParts.getOrNull(1)?.padStart(2, '0') ?: "00"
            val second = timeParts.getOrNull(2)?.padStart(2, '0') ?: "00"

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val slotDateTime = sdf.parse("$datePart $hour:$minute:$second")
            val now = System.currentTimeMillis()
            (slotDateTime?.time ?: Long.MAX_VALUE) <= now
        } catch (e: Exception) {
            false
        }
    }

    // Helper: checks if slot is outside 7-day advance booking window
    fun isSlotBeyond7Days(slot: EnergySlot): Boolean {
        return try {
            val datePart = slot.slotDate.split("T")[0].trim()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
                timeZone = TimeZone.getDefault()
            }
            val slotDate = sdf.parse(datePart) ?: return false
            val now = System.currentTimeMillis()
            val diffMs = slotDate.time - now
            val diffDays = diffMs / (1000.0 * 60 * 60 * 24)
            diffDays > 7.0
        } catch (e: Exception) {
            false
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}
