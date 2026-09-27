import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog"
import type { Station } from "@/types/station"
import { MapContainer, TileLayer, Marker } from "react-leaflet"
import "leaflet/dist/leaflet.css"
import L from "leaflet"

// leaflet's default marker icons break in Vite due to asset bundling; this sets them manually
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

interface ViewStationDialogProps {
  station: Station | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

function DetailRow({
  label,
  value,
}: {
  label: string
  value: React.ReactNode
}) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
        {label}
      </span>
      <span className="text-sm font-medium">{value}</span>
    </div>
  )
}

export function ViewStationDialog({
  station,
  open,
  onOpenChange,
}: ViewStationDialogProps) {
  if (!station) return null

  const position: [number, number] = [station.latitude, station.longitude]

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleString("en-LK", {
      dateStyle: "medium",
      timeStyle: "short",
    })

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[700px]">
        <DialogHeader>
          <DialogTitle className="text-lg">{station.stationName}</DialogTitle>
          <DialogDescription>
            Station details &amp; location overview
          </DialogDescription>
        </DialogHeader>

        <div className="grid grid-cols-1 gap-6 py-2 md:grid-cols-2">
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-3 rounded-lg border bg-muted/30 p-4">
              <DetailRow label="Station ID" value={station.id} />
              <DetailRow label="Station Name" value={station.stationName} />
              <DetailRow label="Location" value={station.location} />
              <DetailRow
                label="Capacity"
                value={`${station.totalCapacityKwh.toLocaleString()} kWh`}
              />
              <DetailRow
                label="Status"
                value={
                  <span
                    className={
                      station.isActive
                        ? "rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700"
                        : "rounded-full bg-red-100 px-2 py-0.5 text-xs font-medium text-red-700"
                    }
                  >
                    {station.isActive ? "Active" : "Inactive"}
                  </span>
                }
              />
            </div>

            <div className="flex flex-col gap-3 rounded-lg border bg-muted/30 p-4">
              <DetailRow label="Latitude" value={station.latitude.toFixed(6)} />
              <DetailRow
                label="Longitude"
                value={station.longitude.toFixed(6)}
              />
            </div>

            <div className="flex flex-col gap-3 rounded-lg border bg-muted/30 p-4">
              <DetailRow
                label="Created At"
                value={formatDate(station.createdAt)}
              />
              <DetailRow
                label="Updated At"
                value={formatDate(station.updatedAt)}
              />
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
              Location on Map
            </span>
            <div className="relative z-0 h-[300px] w-full overflow-hidden rounded-lg border">
              <MapContainer
                center={position}
                zoom={13}
                scrollWheelZoom={false}
                style={{ height: "100%", width: "100%" }}
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <Marker position={position} />
              </MapContainer>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
