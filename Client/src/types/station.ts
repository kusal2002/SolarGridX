// Data model representing a solar charging station and its operating schedule
export interface Station {
  id: string
  stationName: string
  operatorNIC?: string | null
  operatorNICs?: string[]
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
