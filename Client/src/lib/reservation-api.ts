import type {
  EnergyReservation,
  CreateReservationPayload,
  UpdateReservationPayload,
  EnergySlot,
} from "@/types/reservation"

const API_URL = import.meta.env.VITE_API_URL

if (!API_URL) {
  throw new Error("VITE_API_URL is not configured.")
}

async function request<T>(
  path: string,
  options: RequestInit = {}
): Promise<T> {
  const token = localStorage.getItem("solargridx-token")

  const headers = new Headers(options.headers)

  headers.set("Content" + String.fromCharCode(45) + "Type", "application/json")

  if (token) {
    headers.set("Authorization", `Bearer ${token}`)
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("solargridx-token")
      localStorage.removeItem("solargridx-user")

      window.dispatchEvent(new Event("solargridx:logout"))
    }

    const errorData = await response.json().catch(() => ({}))

    throw new Error(
      errorData.message || "The request could not be completed."
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

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