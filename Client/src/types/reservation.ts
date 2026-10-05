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

export interface CreateReservationPayload {
  prosumerNIC: string
  slotId: string
  requestedEnergyKwh: number
}

export interface UpdateReservationPayload {
  newSlotId?: string
  requestedEnergyKwh?: number
}

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
