import type {
  EnergyReservation,
  CreateReservationPayload,
  UpdateReservationPayload,
  EnergySlot,
} from "@/types/reservation"

import { request } from "./api-client"

// Fetches all energy reservations across the grid
export async function getReservations(): Promise<EnergyReservation[]> {
  return request<EnergyReservation[]>("/reservation")
}

// Fetches a single reservation by unique ID
export async function getReservationById(
  id: string
): Promise<EnergyReservation> {
  return request<EnergyReservation>(`/reservation/${id}`)
}

// Fetches reservations belonging to a specific prosumer NIC
export async function getProsumerReservations(
  nic: string
): Promise<EnergyReservation[]> {
  return request<EnergyReservation[]>(
    `/reservation/prosumer/${encodeURIComponent(nic)}`
  )
}

// Submits a new energy slot booking reservation
export async function createReservation(
  data: CreateReservationPayload
): Promise<EnergyReservation> {
  return request<EnergyReservation>("/reservation", {
    method: "POST",
    body: JSON.stringify(data),
  })
}

// Updates reservation details such as booked capacity
export async function updateReservation(
  id: string,
  data: UpdateReservationPayload
): Promise<EnergyReservation> {
  return request<EnergyReservation>(`/reservation/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
}

// Cancels an existing reservation with optional cancellation reason
export async function cancelReservation(
  id: string,
  reason?: string
): Promise<EnergyReservation> {
  const query = reason
    ? `?reason=${encodeURIComponent(reason)}`
    : ""

  return request<EnergyReservation>(
    `/reservation/${id}/cancel${query}`,
    {
      method: "PATCH",
    }
  )
}

// Updates the workflow status of a reservation
export async function updateReservationStatus(
  id: string,
  status: "Pending" | "Approved" | "Completed" | "Cancelled"
): Promise<EnergyReservation> {
  return request<EnergyReservation>(
    `/reservation/${id}/status`,
    {
      method: "PATCH",
      body: JSON.stringify({ status }),
    }
  )
}

// Fetches active energy slots available for reservation
export async function getActiveSlots(): Promise<EnergySlot[]> {
  return request<EnergySlot[]>("/slots")
}