import type {
  EnergyReservation,
  CreateReservationPayload,
  UpdateReservationPayload,
  EnergySlot,
} from "@/types/reservation"

import { request } from "./api-client"

export async function getReservations(): Promise<EnergyReservation[]> {
  return request<EnergyReservation[]>("/reservation")
}

export async function getReservationById(
  id: string
): Promise<EnergyReservation> {
  return request<EnergyReservation>(`/reservation/${id}`)
}

export async function getProsumerReservations(
  nic: string
): Promise<EnergyReservation[]> {
  return request<EnergyReservation[]>(
    `/reservation/prosumer/${encodeURIComponent(nic)}`
  )
}

export async function createReservation(
  data: CreateReservationPayload
): Promise<EnergyReservation> {
  return request<EnergyReservation>("/reservation", {
    method: "POST",
    body: JSON.stringify(data),
  })
}

export async function updateReservation(
  id: string,
  data: UpdateReservationPayload
): Promise<EnergyReservation> {
  return request<EnergyReservation>(`/reservation/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
}

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

export async function getActiveSlots(): Promise<EnergySlot[]> {
  return request<EnergySlot[]>("/slots")
}