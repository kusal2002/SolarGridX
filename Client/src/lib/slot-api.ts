import { request } from "./api-client"
import type { Slot } from "@/types/slot"
export async function getSlots(includeInactive = false): Promise<Slot[]> {
  const endpoint = includeInactive ? "/slots/all" : "/slots"
  return request<Slot[]>(endpoint)
}

export async function getSlotsByStationId(stationId: string, includeInactive = false): Promise<Slot[]> {
  const endpoint = includeInactive 
    ? `/slots/station/${encodeURIComponent(stationId)}/all` 
    : `/slots/station/${encodeURIComponent(stationId)}`
  return request<Slot[]>(endpoint)
}

export async function createSlot(data: {
  stationId: string
  slotDate: string
  startTime: string
  endTime: string
  energyCapacityKwh: number
}) {
  return request<Slot>("/slots", {
    method: "POST",
    body: JSON.stringify(data),
  })
}

export async function updateSlot(
  id: string,
  data: {
    slotDate: string
    startTime: string
    endTime: string
    energyCapacityKwh: number
  }
) {
  return request<Slot>(`/slots/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
}

export async function deactivateSlot(id: string) {
  return request<{ message: string; slot: Slot }>(`/slots/${encodeURIComponent(id)}/deactivate`, {
    method: "PATCH",
  })
}

export async function reactivateSlot(id: string) {
  return request<{ message: string; slot: Slot }>(`/slots/${encodeURIComponent(id)}/reactivate`, {
    method: "PATCH",
  })
}