// Data model representing a booked energy reservation
export interface EnergyReservation {
  id: string
  prosumerName?: string
  prosumerNIC: string
  stationId: string
  stationName?: string
  slotId: string
  transferId?: string | null
  reservationDate: string
  startTime: string
  endTime: string
  requestedEnergyKwh: number
  status: "Pending" | "Approved" | "InProgress" | "Cancelled" | "Completed"
  cancellationReason?: string
  createdAt: string
  updatedAt: string
}

// Request payload for creating a new slot reservation
export interface CreateReservationPayload {
  prosumerNIC: string
  slotId: string
  requestedEnergyKwh: number
}

// Request payload for modifying an existing reservation
export interface UpdateReservationPayload {
  newSlotId?: string
  requestedEnergyKwh?: number
}

// Data model representing an active energy slot for booking
export interface EnergySlot {
  id: string
  stationId: string
  slotDate: string
  startTime: string
  endTime: string
  energyCapacityKwh: number
  availableEnergyKwh: number
  isActive: boolean
}
