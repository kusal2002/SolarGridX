import type { Station } from "@/types/station"

import { request } from "./api-client"

// Fetches list of charging stations, with option to include inactive stations
export async function getStations(includeInactive = false): Promise<Station[]> {
  const endpoint = includeInactive ? "/stations/all" : "/stations"
  return request<Station[]>(endpoint)
}

// Creates a new charging station with assigned operators and capacity
export async function createStation(data: {
  operatorNICs?: string[]
  stationName: string
  location: string
  latitude: number
  longitude: number
  totalCapacityKwh: number
  operatingStartTime: string
  operatingEndTime: string
}) {
  return request<Station>("/stations", {
    method: "POST",
    body: JSON.stringify(data),
  })
}

// Updates station details and operating schedule
export async function updateStation(
  id: string,
  data: {
    stationName: string
    location: string
    latitude: number
    longitude: number
    totalCapacityKwh: number
    operatingStartTime: string
    operatingEndTime: string
    isActive: boolean
  }
) {
  return request<Station>(`/stations/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
}

// Deactivates a station (soft-delete)
export async function deactivateStation(id: string) {
  return request<unknown>(`/stations/${id}`, {
    method: "DELETE",
  })
}

// Reactivates a previously deactivated station
export async function reactivateStation(id: string) {
  return request<unknown>(`/stations/${id}/reactivate`, {
    method: "PATCH",
  })
}