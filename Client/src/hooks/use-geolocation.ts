import { useState, useCallback, useRef } from "react"

interface GeolocationState {
  latitude: number
  longitude: number
  approximate?: boolean // true when from IP fallback
}

// Custom hook to acquire user GPS coordinates with IP-based fallback
export function useGeolocation() {
  const [location, setLocation] = useState<GeolocationState | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [approximate, setApproximate] = useState(false)
  const watchIdRef = useRef<number | null>(null)
  const timeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  const stopWatching = () => {
    if (watchIdRef.current !== null) {
      navigator.geolocation.clearWatch(watchIdRef.current)
      watchIdRef.current = null
    }
    if (timeoutRef.current !== null) {
      clearTimeout(timeoutRef.current)
      timeoutRef.current = null
    }
  }

  // Attempts approximate location lookup using IP address if GPS fails
  const tryIpFallback = useCallback(async () => {
    try {
      const res = await fetch("https://ipwho.is/")
      const data = await res.json()
      if (data && data.success && data.latitude && data.longitude) {
        setLocation({ latitude: data.latitude, longitude: data.longitude, approximate: true })
        setApproximate(true)
        setError("Precise GPS unavailable — showing approximate location based on your IP address. Drag the map pin or enter coordinates to set the exact location.")
      } else {
        setError("Unable to determine location. Please pin your location on the map or enter coordinates manually.")
      }
    } catch {
      setError("Unable to determine location. Please pin your location on the map or enter coordinates manually.")
    } finally {
      setLoading(false)
    }
  }, [])

  // Requests browser geolocation using watchPosition with timeout fallback
  const getLocation = useCallback(() => {
    if (!navigator.geolocation) {
      setError("Geolocation is not supported by your browser")
      return
    }

    stopWatching()
    setLoading(true)
    setError(null)
    setApproximate(false)

    // watchPosition retries on transient kCLErrorLocationUnknown instead of failing immediately
    watchIdRef.current = navigator.geolocation.watchPosition(
      (position) => {
        stopWatching()
        setApproximate(false)
        setLocation({
          latitude: position.coords.latitude,
          longitude: position.coords.longitude,
          approximate: false,
        })
        setLoading(false)
      },
      (err) => {
        if (err.code === err.PERMISSION_DENIED) {
          stopWatching()
          setLoading(false)
          setError("Location access was denied. Please allow location access in your browser settings.")
        }
        // POSITION_UNAVAILABLE / TIMEOUT — wait for the 10s timeout below, then use IP fallback
      },
      {
        enableHighAccuracy: false,
        timeout: 15000,
        maximumAge: 0,
      }
    )

    // After 10s of no GPS fix, fall back to IP geolocation
    timeoutRef.current = setTimeout(() => {
      if (watchIdRef.current !== null) {
        stopWatching()
        tryIpFallback()
      }
    }, 10000)
  }, [tryIpFallback])

  return {
    location,
    loading,
    error,
    approximate,
    getLocation,
  }
}
