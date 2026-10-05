// Base HTTP client wrapper for making authenticated fetch requests to the SolarGridX API
// API base URL configuration from environment variables or local fallback
export const API_URL = import.meta.env.VITE_API_URL || "http://localhost:5084/api"

// Helper function for API calls with JWT bearer token attachment and error handling
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem("solargridx-token")

  const headers = new Headers(options.headers)
  if (!headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json")
  }

  // Attach stored JWT authorization header if available
  if (token) {
    headers.set("Authorization", `Bearer ${token}`)
  }

  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers,
  })

  // Handle unauthorized responses or API errors
  if (!response.ok) {
    if (response.status === 401) {
      // Clear token and trigger logout on 401 Unauthorized
      localStorage.removeItem("solargridx-token")
      localStorage.removeItem("solargridx-user")
      window.dispatchEvent(new Event("solargridx:logout"))
    }

    let errorMessage = "The request could not be completed."
    try {
      const body = await response.json()
      if (body.message) {
        errorMessage = body.message
      } else if (body.title) {
        errorMessage = body.title
      } else if (typeof body === "string") {
        errorMessage = body
      }
    } catch {
      // Failed to parse JSON error response, use default message
    }

    throw new Error(errorMessage)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return response.json() as Promise<T>
}
