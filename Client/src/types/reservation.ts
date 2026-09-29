export interface EnergyReservation {
  id: string
  prosumerNIC: string
  stationId: string
  stationName?: string
  slotId: string
  reservationDate: string
  startTime: string
  endTime: string
  requestedEnergyKwh: number
  status: "Pending" | "Approved" | "Cancelled" | "Completed"
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
