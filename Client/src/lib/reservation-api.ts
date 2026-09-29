import type {
  EnergyReservation,
  CreateReservationPayload,
  UpdateReservationPayload,
  EnergySlot,
} from "@/types/reservation"

const API_URL = import.meta.env.VITE_API_URL || "https://localhost:7172/api"

export async function getReservations(): Promise<EnergyReservation[]> {
  const response = await fetch(`${API_URL}/reservation`)
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to fetch reservations")
  }
  return response.json()
}

export async function getReservationById(id: string): Promise<EnergyReservation> {
  const response = await fetch(`${API_URL}/reservation/${id}`)
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to fetch reservation")
  }
  return response.json()
}

export async function getProsumerReservations(
  nic: string
): Promise<EnergyReservation[]> {
  const response = await fetch(`${API_URL}/reservation/prosumer/${encodeURIComponent(nic)}`)
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to fetch prosumer reservations")
  }
  return response.json()
}

export async function createReservation(
  data: CreateReservationPayload
): Promise<EnergyReservation> {
  const response = await fetch(`${API_URL}/reservation`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to create reservation")
  }

  return response.json()
}

export async function updateReservation(
  id: string,
  data: UpdateReservationPayload
): Promise<EnergyReservation> {
  const response = await fetch(`${API_URL}/reservation/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to update reservation")
  }

  return response.json()
}

export async function cancelReservation(
  id: string,
  reason?: string
): Promise<EnergyReservation> {
  const query = reason ? `?reason=${encodeURIComponent(reason)}` : ""
  const response = await fetch(`${API_URL}/reservation/${id}/cancel${query}`, {
    method: "PATCH",
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to cancel reservation")
  }

  return response.json()
}

export async function updateReservationStatus(
  id: string,
  status: "Pending" | "Approved" | "Completed" | "Cancelled"
): Promise<EnergyReservation> {
  const response = await fetch(`${API_URL}/reservation/${id}/status`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ status }),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to update reservation status")
  }

  return response.json()
}

export async function getActiveSlots(): Promise<EnergySlot[]> {
  const response = await fetch(`${API_URL}/slots`)
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to fetch slots")
  }
  return response.json()
}
