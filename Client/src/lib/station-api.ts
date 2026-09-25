const API_URL = "http://localhost:5084/api"

export async function getStations() {
  const response = await fetch(`${API_URL}/stations/all`)

  if (!response.ok) {
    throw new Error("Failed to fetch stations")
  }

  return response.json()
}
