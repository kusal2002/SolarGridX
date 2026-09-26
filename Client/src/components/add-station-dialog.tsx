import { useState, useEffect } from "react"
import { Plus, Search, MapPin } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { createStation } from "@/lib/station-api"
import type { Station } from "@/types/station"
import { useGeolocation } from "@/hooks/use-geolocation"
import {
  MapContainer,
  TileLayer,
  Marker,
  useMapEvents,
  useMap,
} from "react-leaflet"
import "leaflet/dist/leaflet.css"
import L from "leaflet"

// Fix for default marker icon in leaflet + vite
const defaultIcon = L.icon({
  iconUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon.png",
  iconRetinaUrl:
    "https://unpkg.com/leaflet@1.9.4/dist/images/marker-icon-2x.png",
  shadowUrl: "https://unpkg.com/leaflet@1.9.4/dist/images/marker-shadow.png",
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  tooltipAnchor: [16, -28],
  shadowSize: [41, 41],
})
L.Marker.prototype.options.icon = defaultIcon

interface AddStationDialogProps {
  onStationAdded: (station: Station) => void
}

function MapEvents({
  onLocationSelect,
}: {
  onLocationSelect: (lat: number, lng: number) => void
}) {
  useMapEvents({
    click(e) {
      onLocationSelect(e.latlng.lat, e.latlng.lng)
    },
  })
  return null
}

function MapCenter({
  position,
  zoom,
}: {
  position: [number, number]
  zoom: number
}) {
  const map = useMap()
  useEffect(() => {
    map.setView(position, zoom)
  }, [position, zoom, map])
  return null
}

export function AddStationDialog({ onStationAdded }: AddStationDialogProps) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const [formData, setFormData] = useState({
    stationName: "",
    location: "",
    latitude: "",
    longitude: "",
    totalCapacityKwh: "",
  })

  const [searchQuery, setSearchQuery] = useState("")
  const [isSearching, setIsSearching] = useState(false)

  const {
    location: gpsLocation,
    loading: gpsLoading,
    getLocation,
  } = useGeolocation()

  useEffect(() => {
    if (gpsLocation) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFormData((prev) => ({
        ...prev,
        latitude: gpsLocation.latitude.toString(),
        longitude: gpsLocation.longitude.toString(),
      }))
    }
  }, [gpsLocation])

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  const handleLocationSelect = async (lat: number, lng: number) => {
    setFormData((prev) => ({
      ...prev,
      latitude: lat.toString(),
      longitude: lng.toString(),
    }))
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/reverse?format=json&lat=${lat}&lon=${lng}`
      )
      const data = await res.json()
      if (data && data.display_name) {
        // Use a more specific part of the address if possible, else the first part
        const locationName =
          data.address?.city ||
          data.address?.town ||
          data.address?.village ||
          data.display_name.split(",")[0]
        setFormData((prev) => ({ ...prev, location: locationName }))
      }
    } catch (err) {
      console.error("Reverse geocoding failed", err)
    }
  }

  const handleSearch = async () => {
    if (!searchQuery) return
    setIsSearching(true)
    try {
      const res = await fetch(
        `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(searchQuery)}&countrycodes=LK`
      )
      const data = await res.json()
      if (data && data.length > 0) {
        const { lat, lon, display_name } = data[0]
        setFormData((prev) => ({
          ...prev,
          latitude: lat,
          longitude: lon,
          location: display_name.split(",")[0],
        }))
      } else {
        setError("Location not found.")
      }
    } catch (err) {
      console.error(err)
      setError("Search failed.")
    } finally {
      setIsSearching(false)
    }
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError("")

    try {
      const newStation = await createStation({
        stationName: formData.stationName,
        location: formData.location,
        latitude: parseFloat(formData.latitude),
        longitude: parseFloat(formData.longitude),
        totalCapacityKwh: parseFloat(formData.totalCapacityKwh),
      })

      onStationAdded(newStation)
      setOpen(false)
      setFormData({
        stationName: "",
        location: "",
        latitude: "",
        longitude: "",
        totalCapacityKwh: "",
      })
      setSearchQuery("")
    } catch (err) {
      console.error(err)
      setError("Failed to create station. Please try again.")
    } finally {
      setLoading(false)
    }
  }

  // Sri Lanka Center
  const defaultCenter: [number, number] = [7.8731, 80.7718]
  const mapCenter: [number, number] =
    formData.latitude && formData.longitude
      ? [parseFloat(formData.latitude), parseFloat(formData.longitude)]
      : defaultCenter
  const mapZoom = formData.latitude && formData.longitude ? 13 : 7

  // Sri Lanka Map Bounds
  const sriLankaBounds: L.LatLngBoundsExpression = [
    [5.8, 79.5], // Southwest
    [9.9, 82.0], // Northeast
  ]

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button />}>
        <Plus className="mr-2 size-4" />
        Add Station
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[800px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Add New Station</DialogTitle>
            <DialogDescription>
              Enter the details of the new solar station to add to the grid.
            </DialogDescription>
          </DialogHeader>

          {error && (
            <p className="mt-4 rounded-md bg-red-50 p-2 text-sm text-red-500">
              {error}
            </p>
          )}

          <div className="grid grid-cols-1 gap-6 py-4 md:grid-cols-2">
            <div className="flex flex-col gap-4">
              <div className="grid gap-2">
                <Label htmlFor="stationName">Station Name</Label>
                <Input
                  id="stationName"
                  name="stationName"
                  placeholder="e.g. North Ridge Alpha"
                  value={formData.stationName}
                  onChange={handleChange}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="location">Location</Label>
                <Input
                  id="location"
                  name="location"
                  placeholder="e.g. North District"
                  value={formData.location}
                  onChange={handleChange}
                  required
                />
              </div>
              <div className="flex items-center justify-between">
                <Label>Coordinates</Label>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  onClick={getLocation}
                  disabled={gpsLoading}
                >
                  <MapPin className="mr-2 size-3" />
                  {gpsLoading ? "Getting Location..." : "Get My Location"}
                </Button>
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="latitude">Latitude</Label>
                  <Input
                    id="latitude"
                    name="latitude"
                    type="number"
                    step="any"
                    placeholder="0.00"
                    value={formData.latitude}
                    onChange={handleChange}
                    required
                  />
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="longitude">Longitude</Label>
                  <Input
                    id="longitude"
                    name="longitude"
                    type="number"
                    step="any"
                    placeholder="0.00"
                    value={formData.longitude}
                    onChange={handleChange}
                    required
                  />
                </div>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="totalCapacityKwh">Total Capacity (kWh)</Label>
                <Input
                  id="totalCapacityKwh"
                  name="totalCapacityKwh"
                  type="number"
                  step="any"
                  min="0"
                  placeholder="e.g. 500"
                  value={formData.totalCapacityKwh}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="flex flex-col gap-4">
              <div className="flex gap-2">
                <Input
                  placeholder="Search location..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault()
                      handleSearch()
                    }
                  }}
                />
                <Button
                  type="button"
                  variant="secondary"
                  onClick={handleSearch}
                  disabled={isSearching}
                >
                  <Search className="size-4" />
                </Button>
              </div>

              <div className="relative z-0 h-[300px] w-full overflow-hidden rounded-md border">
                <MapContainer
                  center={mapCenter}
                  zoom={mapZoom}
                  minZoom={7}
                  maxBounds={sriLankaBounds}
                  maxBoundsViscosity={1.0}
                  scrollWheelZoom={true}
                  style={{ height: "100%", width: "100%" }}
                >
                  <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                  />
                  {formData.latitude && formData.longitude && (
                    <Marker
                      position={[
                        parseFloat(formData.latitude),
                        parseFloat(formData.longitude),
                      ]}
                    />
                  )}
                  <MapEvents onLocationSelect={handleLocationSelect} />
                  <MapCenter position={mapCenter} zoom={mapZoom} />
                </MapContainer>
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? "Adding..." : "Add Station"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
