import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import type { Station } from "@/types/station"
import { MapContainer, TileLayer, Marker } from "react-leaflet"
import "leaflet/dist/leaflet.css"
import L from "leaflet"
import { Activity, Battery, CalendarDays, Clock, Hash, MapPin, Zap } from "lucide-react"

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

// Card displaying an individual station property with icon and label
function DetailCard({
  icon,
  label,
  value,
  highlight = false,
}: {
  icon: React.ReactNode
  label: string
  value: React.ReactNode
  highlight?: boolean
}) {
  return (
    <div className={`flex items-start gap-4 rounded-xl border p-4 transition-all hover:bg-muted/50 ${highlight ? "border-primary/50 bg-primary/5 shadow-sm" : "bg-card"}`}>
      <div className={`mt-0.5 shrink-0 rounded-full p-2 ${highlight ? "bg-primary/20 text-primary" : "bg-muted text-muted-foreground"}`}>
        {icon}
      </div>
      <div className="flex min-w-0 flex-col gap-1">
        <span className="text-xs font-semibold tracking-wider text-muted-foreground uppercase">
          {label}
        </span>
        <div className={`break-all text-sm font-medium ${highlight ? "text-primary" : "text-foreground"}`}>
          {value}
        </div>
      </div>
    </div>
  )
}

// Read-only modal displaying detailed station information and interactive Leaflet map
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
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-[850px] p-0 gap-0 overflow-hidden border-none shadow-2xl">
        <div className="bg-gradient-to-br from-primary/10 via-background to-background px-6 py-8 border-b">
          <DialogHeader>
            <DialogTitle className="text-3xl font-bold tracking-tight flex items-center gap-2">
              <Zap className="size-6 text-primary fill-primary" />
              {station.stationName}
            </DialogTitle>
          </DialogHeader>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-5 bg-background">
          <div className="col-span-3 p-6 space-y-6 max-h-[60vh] overflow-y-auto">
            
            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider">Overview</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <DetailCard
                  icon={<Hash className="size-4" />}
                  label="Station ID"
                  value={<span className="font-mono text-xs">{station.id}</span>}
                />
                <DetailCard
                  icon={<Activity className="size-4" />}
                  label="Status"
                  value={
                    <span className="flex items-center gap-2">
                      <span className={`relative flex size-2.5`}>
                        {station.isActive && (
                          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-green-400 opacity-75"></span>
                        )}
                        <span className={`relative inline-flex size-2.5 rounded-full ${station.isActive ? "bg-green-500" : "bg-red-500"}`}></span>
                      </span>
                      {station.isActive ? "Online & Active" : "Offline / Inactive"}
                    </span>
                  }
                  highlight={station.isActive}
                />
              </div>
            </div>

            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider">Capacity & Schedule</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <DetailCard
                  icon={<Battery className="size-4" />}
                  label="Total Capacity"
                  value={`${station.totalCapacityKwh.toLocaleString()} kWh`}
                  highlight
                />
                <DetailCard
                  icon={<Clock className="size-4" />}
                  label="Operating Hours"
                  value={
                    station.operatingStartTime && station.operatingEndTime 
                      ? `${station.operatingStartTime.slice(0, 5)} - ${station.operatingEndTime.slice(0, 5)}` 
                      : "24/7 (Not Specified)"
                  }
                />
              </div>
            </div>

            <div className="space-y-4">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider">System Info</h3>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <DetailCard
                  icon={<CalendarDays className="size-4" />}
                  label="Created At"
                  value={formatDate(station.createdAt)}
                />
                <DetailCard
                  icon={<CalendarDays className="size-4" />}
                  label="Last Updated"
                  value={formatDate(station.updatedAt)}
                />
              </div>
            </div>
            
          </div>

          <div className="col-span-2 border-l bg-muted/20 relative min-h-[300px]">
            <div className="absolute inset-0">
              <MapContainer
                center={position}
                zoom={14}
                scrollWheelZoom={false}
                style={{ height: "100%", width: "100%" }}
                className="z-0"
              >
                <TileLayer
                  attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                  url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <Marker position={position} />
              </MapContainer>
            </div>
            <div className="absolute bottom-4 left-4 right-4 z-10">
              <div className="flex items-start gap-3 rounded-xl bg-background/95 backdrop-blur border p-4 shadow-lg">
                <MapPin className="size-5 text-primary shrink-0 mt-0.5" />
                <div className="flex flex-col">
                  <span className="text-xs font-semibold uppercase text-muted-foreground">Location</span>
                  <span className="text-sm font-medium">{station.location}</span>
                  <span className="text-xs text-muted-foreground mt-1">
                    {station.latitude.toFixed(6)}, {station.longitude.toFixed(6)}
                  </span>
                </div>
              </div>
            </div>
          </div>

        </div>
      </DialogContent>
    </Dialog>
  )
}
