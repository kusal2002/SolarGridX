import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table"
import { StatusBadge } from "@/components/status-badge"
import { useEffect, useState } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { useAuth } from "@/context/AuthContext"
import { request } from "@/lib/api-client"

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

  const [actionError, setActionError] = useState("")
  const [operators, setOperators] = useState<{ nic: string; name: string }[]>([])
  const [assigning, setAssigning] = useState("")

  const { user } = useAuth()
  const isBackoffice = user?.role === "Backoffice"
  const canManageStations = isBackoffice
  const canViewInactive = isBackoffice || user?.role === "Grid Operator"

  useEffect(() => {
    async function loadStations() {
      try {
        setLoading(true)
        setError("")
        const data = await getStations(canViewInactive)
        setStations(data)
        if (isBackoffice) setOperators(await request<{ nic: string; name: string }[]>("/stations/operators"))
      } catch (error) {
        console.error(error)
        setError("Unable to load stations.")
      } finally {
        setLoading(false)
      }
    }

    loadStations()
  }, [canViewInactive, isBackoffice])

  async function assignOperator(station: Station, operatorNIC: string) {
    setAssigning(station.id)
    try {
      const updated = await request<Station>(`/stations/${station.id}/operator`, { method: "PATCH", body: JSON.stringify({ operatorNIC: operatorNIC || null }) })
      setStations(previous => previous.map(s => s.id === updated.id ? updated : s))
    } catch (e) { setActionError(e instanceof Error ? e.message : "Assignment failed") }
    finally { setAssigning("") }
  }

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
      setActionError("")
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
    } catch (err: unknown) {
      if (err instanceof Error) {
        setActionError(err.message || "Failed to change station status.")
      } else {
        setActionError("Failed to change station status.")
      }
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

        {canManageStations && (
          <AddStationDialog
            onStationAdded={(newStation) =>
              setStations([newStation, ...stations])
            }
          />
        )}
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
          <Table className="w-full text-left text-sm">
            <TableHeader className="bg-muted/50">
              <TableRow className="border-b">
                <TableHead className="px-4 py-3 font-medium">Station ID</TableHead>
                <TableHead className="px-4 py-3 font-medium">Station Name</TableHead>
                <TableHead className="px-4 py-3 font-medium">Location</TableHead>
                <TableHead className="px-4 py-3 font-medium">Capacity (kWh)</TableHead>
                <TableHead className="px-4 py-3 font-medium">Status</TableHead>
                {canManageStations && (
                  <TableHead className="px-4 py-3 text-right font-medium">Actions</TableHead>
                )}
                <TableHead className="px-4 py-3 font-medium">Grid Operator</TableHead>
              </TableRow>
            </TableHeader>

            <TableBody>
              {filteredStations.map((station) => (
                <TableRow
                  key={station.id}
                  className="cursor-pointer border-b transition-colors last:border-0 hover:bg-muted/30"
                  onClick={() => handleRowClick(station)}
                >
                  <TableCell className="px-4 py-3 font-medium">{station.id}</TableCell>
                  <TableCell className="px-4 py-3">{station.stationName}</TableCell>
                  <TableCell className="px-4 py-3">{station.location}</TableCell>
                  <TableCell className="px-4 py-3">{station.totalCapacityKwh}</TableCell>
                  <TableCell className="px-4 py-3">
                    <StatusBadge status={station.isActive ? "Active" : "Inactive"} />
                  </TableCell>
                  {canManageStations && (
                    <TableCell className="px-4 py-3">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="outline"
                          size="icon"
                          onClick={(e) => handleEditClick(e, station)}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        <Button
                          variant="outline"
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
                    </TableCell>
                  )}
                  <TableCell className="px-4 py-3" onClick={e => e.stopPropagation()}>
                    {isBackoffice ? <select aria-label={`Operator for ${station.stationName}`} className="rounded-md border bg-background p-2" disabled={assigning === station.id} value={station.operatorNIC ?? ""} onChange={e => void assignOperator(station, e.target.value)}>
                      <option value="">Unassigned</option>
                      {station.operatorNIC && !operators.some(o => o.nic === station.operatorNIC) && <option value={station.operatorNIC}>Inactive operator ({station.operatorNIC})</option>}
                      {operators.map(o => <option key={o.nic} value={o.nic}>{o.name} ({o.nic})</option>)}
                    </select> : station.operatorNIC ?? "Unassigned"}
                  </TableCell>
                </TableRow>
              ))}

              {filteredStations.length === 0 && (
                <TableRow>
                  <TableCell
                    colSpan={canManageStations ? 7 : 6}
                    className="px-4 py-8 text-center text-muted-foreground"
                  >
                    {!isBackoffice && !stations.length ? "No stations assigned. Ask Backoffice to assign your account to a station." : "No stations found."}
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
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

      <Dialog open={!!actionError} onOpenChange={(open) => !open && setActionError("")}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>Unable to Deactivate Station</DialogTitle>
            <DialogDescription className="text-red-600 mt-2">
              {actionError}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button onClick={() => setActionError("")}>OK</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
