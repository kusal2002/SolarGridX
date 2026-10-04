export interface Slot {
  id: string
  stationId: string
  slotDate: string
  startTime: string
  endTime: string
  energyCapacityKwh: number
  availableEnergyKwh: number
  isActive: boolean
  createdAt: string
  updatedAt: string
}
