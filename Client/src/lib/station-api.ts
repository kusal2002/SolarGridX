const API_URL = "http://localhost:5084/api"

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
