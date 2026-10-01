import type { Station } from "@/types/station"

import { request } from "./api-client"

export async function getStations(): Promise<Station[]> {
  return request<Station[]>("/stations/all")
}

export async function createStation(data: {
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

export async function deactivateStation(id: string) {
  return request<unknown>(`/stations/${id}`, {
    method: "DELETE",
  })
}

export async function reactivateStation(id: string) {
  return request<unknown>(`/stations/${id}/reactivate`, {
    method: "PATCH",
  })
}