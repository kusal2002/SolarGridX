const API_URL = "http://127.0.0.1:5084/api"

export async function getSlots() {
  const response = await fetch(`${API_URL}/slots`)

  if (!response.ok) {
    throw new Error("Failed to fetch slots")
  }

  return response.json()
}

export async function getSlotsByStationId(stationId: string) {
  const response = await fetch(`${API_URL}/slots/station/${stationId}`)

  if (!response.ok) {
    throw new Error("Failed to fetch slots for station")
  }

  return response.json()
}

export async function createSlot(data: {
  stationId: string
  slotDate: string
  startTime: string
  endTime: string
  energyCapacityKwh: number
}) {
  const response = await fetch(`${API_URL}/slots`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to create slot")
  }

  return response.json()
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
  const response = await fetch(`${API_URL}/slots/${id}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(data),
  })

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}))
    throw new Error(errorData.message || "Failed to update slot")
  }

  return response.json()
}
