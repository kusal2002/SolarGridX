const API_URL = "http://127.0.0.1:5084/api"

export async function getStations() {
  const response = await fetch(`${API_URL}/stations/all`)

  if (!response.ok) {
    throw new Error("Failed to fetch stations")
  }

  return response.json()
}

export async function createStation(data: {
  stationName: string
  location: string
  latitude: number
  longitude: number
  totalCapacityKwh: number
}) {
  const response = await fetch(`${API_URL}/stations`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    throw new Error("Failed to create station")
  }

  return response.json()
}

export async function updateStation(
  id: string,
  data: {
    stationName: string
    location: string
    latitude: number
    longitude: number
    totalCapacityKwh: number
    isActive: boolean
  }
) {
  const response = await fetch(`${API_URL}/stations/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    throw new Error("Failed to update station")
  }

  return response.json()
}

export async function deactivateStation(id: string) {
  const response = await fetch(`${API_URL}/stations/${id}`, {
    method: "DELETE",
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to deactivate station")
  }

  return response.json()
}

export async function reactivateStation(id: string) {
  const response = await fetch(`${API_URL}/stations/${id}/reactivate`, {
    method: "PATCH",
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to reactivate station")
  }

  return response.json()
}
