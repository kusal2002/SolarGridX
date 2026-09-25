import { useEffect, useState } from "react"
import {
  getStations,
  deactivateStation,
  reactivateStation,
} from "@/lib/station-api"
import type { Station } from "@/types/station"
import { Button } from "@/components/ui/button"
import { Search, Pencil, Power } from "lucide-react"
import { AddStationDialog } from "@/components/add-station-dialog"
import { ViewStationDialog } from "@/components/view-station-dialog"
import { EditStationDialog } from "@/components/edit-station-dialog"

export function StationsPage() {
  const [stations, setStations] = useState<Station[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [searchTerm, setSearchTerm] = useState("")
  const [statusFilter, setStatusFilter] = useState<
    "All" | "Active" | "Inactive"
  >("All")
  const [viewStation, setViewStation] = useState<Station | null>(null)
  const [viewOpen, setViewOpen] = useState(false)
  const [editStation, setEditStation] = useState<Station | null>(null)
  const [editOpen, setEditOpen] = useState(false)

  useEffect(() => {
    async function loadStations() {
      try {
        setLoading(true)
        setError("")
        const data = await getStations()
        setStations(data)
      } catch (error) {
        console.error(error)
        setError("Unable to load stations.")
      } finally {
        setLoading(false)
      }
    }

    loadStations()
  }, [])

  const filteredStations = stations
    .filter((station) => {
      const search = searchTerm.toLowerCase()
      const matchesSearch =
        station.stationName.toLowerCase().includes(search) ||
        station.location.toLowerCase().includes(search)
      const matchesStatus =
        statusFilter === "All" ||
        (statusFilter === "Active" && station.isActive) ||
        (statusFilter === "Inactive" && !station.isActive)
      return matchesSearch && matchesStatus
    })
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )

  const totalStations = stations.length
  const activeStations = stations.filter((station) => station.isActive).length

  const handleRowClick = (station: Station) => {
    setViewStation(station)
    setViewOpen(true)
  }

  const handleEditClick = (e: React.MouseEvent, station: Station) => {
    e.stopPropagation() // stop the row click from also triggering the view dialog
    setEditStation(station)
    setEditOpen(true)
  }

  const handleStationUpdated = (updated: Station) => {
    // replace only the edited station in the list without re-fetching
    setStations((prev) => prev.map((s) => (s.id === updated.id ? updated : s)))
  }

  const handleToggleStatus = async (e: React.MouseEvent, station: Station) => {
    e.stopPropagation()
    try {
      if (station.isActive) {
        await deactivateStation(station.id)
      } else {
        await reactivateStation(station.id)
      }

      // Update local state without full refetch
      setStations((prev) =>
        prev.map((s) =>
          s.id === station.id ? { ...s, isActive: !s.isActive } : s
        )
      )
    } catch (err: any) {
      alert(err.message || "Failed to change station status.")
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Station Management
          </h1>
          <p className="text-muted-foreground">
            Manage solar stations and their energy capacity.
          </p>
        </div>

        <AddStationDialog
          onStationAdded={(newStation) =>
            setStations([newStation, ...stations])
          }
        />
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Total Stations</p>
          <p className="mt-2 text-2xl font-semibold">{totalStations}</p>
        </div>

        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Active Stations</p>
          <p className="mt-2 text-2xl font-semibold text-green-600">
            {activeStations}
          </p>
        </div>

        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Inactive Stations</p>
          <p className="mt-2 text-2xl font-semibold text-red-600">
            {totalStations - activeStations}
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="flex items-center gap-2 rounded-lg border px-3 py-2 sm:w-80">
          <Search className="size-4 text-muted-foreground" />
          <input
            type="text"
            placeholder="Search stations..."
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            className="w-full bg-transparent text-sm outline-none"
          />
        </div>

        <select
          value={statusFilter}
          onChange={(event) =>
            setStatusFilter(event.target.value as "All" | "Active" | "Inactive")
          }
          className="rounded-lg border bg-background px-3 py-2 text-sm outline-none"
        >
          <option value="All">All Stations</option>
          <option value="Active">Active Stations</option>
          <option value="Inactive">Inactive Stations</option>
        </select>
      </div>

      {loading && (
        <p className="text-sm text-muted-foreground">Loading stations...</p>
      )}

      {!loading && error && (
        <p className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-500">
          {error}
        </p>
      )}

      {!loading && !error && (
        <div className="overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted/50">
              <tr className="border-b">
                <th className="px-4 py-3 font-medium">Station ID</th>
                <th className="px-4 py-3 font-medium">Station Name</th>
                <th className="px-4 py-3 font-medium">Location</th>
                <th className="px-4 py-3 font-medium">Capacity (kWh)</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>

            <tbody>
              {filteredStations.map((station) => (
                <tr
                  key={station.id}
                  className="cursor-pointer border-b transition-colors last:border-0 hover:bg-muted/30"
                  onClick={() => handleRowClick(station)}
                >
                  <td className="px-4 py-3 font-medium">{station.id}</td>
                  <td className="px-4 py-3">{station.stationName}</td>
                  <td className="px-4 py-3">{station.location}</td>
                  <td className="px-4 py-3">{station.totalCapacityKwh}</td>
                  <td className="px-4 py-3">
                    <span
                      className={
                        station.isActive
                          ? "rounded-full bg-green-100 px-2 py-1 text-xs font-medium text-green-700"
                          : "rounded-full bg-red-100 px-2 py-1 text-xs font-medium text-red-700"
                      }
                    >
                      {station.isActive ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={(e) => handleEditClick(e, station)}
                      >
                        <Pencil className="size-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={(e) => handleToggleStatus(e, station)}
                        title={
                          station.isActive
                            ? "Deactivate Station"
                            : "Reactivate Station"
                        }
                      >
                        <Power
                          className={`size-4 ${station.isActive ? "text-red-500" : "text-green-500"}`}
                        />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}

              {filteredStations.length === 0 && (
                <tr>
                  <td
                    colSpan={6}
                    className="px-4 py-8 text-center text-muted-foreground"
                  >
                    No stations found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <ViewStationDialog
        station={viewStation}
        open={viewOpen}
        onOpenChange={setViewOpen}
      />

      <EditStationDialog
        station={editStation}
        open={editOpen}
        onOpenChange={setEditOpen}
        onStationUpdated={handleStationUpdated}
      />
    </div>
  )
}
