import { request } from "./api-client"
import type { Slot } from "@/types/slot"

// Fetches charging slots, with option to include inactive slots
export async function getSlots(includeInactive = false): Promise<Slot[]> {
  const endpoint = includeInactive ? "/slots/all" : "/slots"
  return request<Slot[]>(endpoint)
}

// Fetches charging slots for a specific station ID
export async function getSlotsByStationId(stationId: string, includeInactive = false): Promise<Slot[]> {
  const endpoint = includeInactive 
    ? `/slots/station/${encodeURIComponent(stationId)}/all` 
    : `/slots/station/${encodeURIComponent(stationId)}`
  return request<Slot[]>(endpoint)
}

// Creates a new charging slot for a station
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

// Updates slot timing and energy capacity
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

// Deactivates an existing charging slot
export async function deactivateSlot(id: string) {
  return request<{ message: string; slot: Slot }>(`/slots/${encodeURIComponent(id)}/deactivate`, {
    method: "PATCH",
  })
}

// Reactivates a previously deactivated charging slot
export async function reactivateSlot(id: string) {
  return request<{ message: string; slot: Slot }>(`/slots/${encodeURIComponent(id)}/reactivate`, {
    method: "PATCH",
  })
}