import { useState, useCallback } from "react"

interface GeolocationState {
  latitude: number
  longitude: number
}

export function useGeolocation() {
  const [location, setLocation] = useState<GeolocationState | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const getLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setError("Geolocation is not supported by your browser")
      return
    }

    setLoading(true)
    setError(null)

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const coords = {
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
        }
        // Cache location in sessionStorage
        sessionStorage.setItem("userLocation", JSON.stringify(coords))
        setLocation(coords)
        setLoading(false)
      },
      (err) => {
        // Fallback to sessionStorage if GPS fails
        const cached = sessionStorage.getItem("userLocation")
        if (cached) {
          try {
            setLocation(JSON.parse(cached))
            setLoading(false)
            return
          } catch {
            // ignore
          }
        }

        // Fallback to IP-based location if GPS fails or is blocked
        fetch("https://ipapi.co/json/")
          .then((res) => res.json())
          .then((data) => {
            if (data && data.latitude && data.longitude) {
              const coords = {
                latitude: data.latitude,
                longitude: data.longitude,
              }
              sessionStorage.setItem("userLocation", JSON.stringify(coords))
              setLocation(coords)
            } else {
              setError(err.message || "Unable to retrieve location")
            }
          })
          .catch((ipErr) => {
            console.error("GPS Error:", err.message, "IP Error:", ipErr)
            setError(err.message)
          })
          .finally(() => {
            setLoading(false)
          })
      },
      {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 300000,
      }
    )
  }, [])

  return {
    location,
    loading,
    error,
    getLocation,
  }
}
