import type { Station } from "@/types/station"

const API_URL = import.meta.env.VITE_API_URL

if (!API_URL) {
  throw new Error("VITE_API_URL is not configured.")
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
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

    const body = await response.json().catch(() => ({}))

    throw new Error(body.message || "The request could not be completed.")
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}

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