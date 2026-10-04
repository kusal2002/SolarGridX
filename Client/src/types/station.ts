export interface Station {
  id: string
  stationName: string
  location: string
  latitude: number
  longitude: number
  totalCapacityKwh: number
  operatingStartTime: string
  operatingEndTime: string
  isActive: boolean
  createdAt: string
  updatedAt: string
}
