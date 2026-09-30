const API_URL =
  import.meta.env.VITE_API_URL || "http://127.0.0.1:5084/api"

const TOKEN_KEY = ["solargridx", "token"].join(String.fromCharCode(45))
const USER_KEY = ["solargridx", "user"].join(String.fromCharCode(45))

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem(TOKEN_KEY)

  const headers = new Headers(options.headers)

  headers.set("Content-Type", "application/json")

  if (token) {
    headers.set("Authorization", `Bearer ${token}`)
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  })

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
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

export async function getSlots() {
  return request("/slots")
}

export async function getSlotsByStationId(stationId: string) {
  return request(`/slots/station/${encodeURIComponent(stationId)}`)
}

export async function createSlot(data: {
  stationId: string
  slotDate: string
  startTime: string
  endTime: string
  energyCapacityKwh: number
}) {
  return request("/slots", {
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
  return request(`/slots/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(data),
  })
}

export async function deactivateSlot(id: string) {
  return request(`/slots/${encodeURIComponent(id)}/deactivate`, {
    method: "PATCH",
  })
}

export async function reactivateSlot(id: string) {
  return request(`/slots/${encodeURIComponent(id)}/reactivate`, {
    method: "PATCH",
  })
}