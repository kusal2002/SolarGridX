import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table"
import { StatusBadge } from "@/components/status-badge"
import { useEffect, useState, useMemo } from "react"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { getSlots, getSlotsByStationId, deactivateSlot, reactivateSlot } from "@/lib/slot-api"
import { getStations } from "@/lib/station-api"
import type { Slot } from "@/types/slot"
import type { Station } from "@/types/station"
import { AddSlotDialog } from "@/components/add-slot-dialog"
import { EditSlotDialog } from "@/components/edit-slot-dialog"
import { Pencil, Power } from "lucide-react"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/context/AuthContext"

export function SlotsPage() {
  const [slots, setSlots] = useState<Slot[]>([])
  const [stations, setStations] = useState<Station[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 60000); return () => window.clearInterval(timer) }, [])
  
  const [editingSlot, setEditingSlot] = useState<Slot | null>(null)
  
  const [actionError, setActionError] = useState("")

  const [stationFilter, setStationFilter] = useState<string>("All")
  const [statusFilter, setStatusFilter] = useState<"All" | "Active" | "Inactive">("All")

  const { user } = useAuth()
  const canManageSlots = user?.role === "Backoffice" || user?.role === "Grid Operator"
  const canViewInactive = canManageSlots

  const canViewInactiveStations = user?.role === "Backoffice" || user?.role === "Grid Operator"

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true)
        setError("")
        
        // Fetch both stations and slots
        const [stationsData, slotsData] = await Promise.all([
          getStations(canViewInactiveStations),
          getSlots(canViewInactive)
        ])
        
        setStations(stationsData)
        setSlots(slotsData)
      } catch (err) {
        console.error(err)
        setError("Unable to load slots data.")
      } finally {
        setLoading(false)
      }
    }

    loadData()
  }, [canViewInactive, canViewInactiveStations])

  const handleStationFilterChange = async (event: React.ChangeEvent<HTMLSelectElement>) => {
    const newStationId = event.target.value
    setStationFilter(newStationId)
    
    try {
      setLoading(true)
      setError("")
      let data: Slot[]
      if (newStationId === "All") {
        data = await getSlots(canViewInactive)
      } else {
        data = await getSlotsByStationId(newStationId, canViewInactive)
      }
      setSlots(data)
    } catch (err) {
      console.error(err)
      setError("Unable to filter slots.")
    } finally {
      setLoading(false)
    }
  }

  const handleSlotUpdated = (updatedSlot: Slot) => {
    setSlots(slots.map(slot => slot.id === updatedSlot.id ? updatedSlot : slot))
  }

  const handleToggleStatus = async (e: React.MouseEvent, slot: Slot) => {
    e.stopPropagation()
    try {
      setActionError("")
      if (slot.isActive) {
        await deactivateSlot(slot.id)
      } else {
        await reactivateSlot(slot.id)
      }

      setSlots((prev) =>
        prev.map((s) =>
          s.id === slot.id ? { ...s, isActive: !s.isActive } : s
        )
      )
    } catch (err: unknown) {
      if (err instanceof Error) {
        setActionError(err.message || "Failed to change slot status.")
      } else {
        setActionError("Failed to change slot status.")
      }
    }
  }

  // Map station ID to station Name for easy display
  const stationMap = useMemo(() => {
    const map = new Map<string, string>()
    stations.forEach(s => map.set(s.id, s.stationName))
    return map
  }, [stations])

  const isPastSlot = (slot: Slot) => {
    const parts = new Intl.DateTimeFormat("en", { timeZone: "Asia/Colombo", year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(new Date(slot.slotDate))
    const value = (type: string) => Number(parts.find(p => p.type === type)?.value)
    const [hour, minute, second = 0] = slot.endTime.split(":").map(Number)
    const end = Date.UTC(value("year"), value("month") - 1, value("day"), hour, minute, second) - 330 * 60000
    return end <= now
  }
  const effectiveSlots = slots.map(slot => isPastSlot(slot) ? { ...slot, isActive: false } : slot)
  const filteredSlots = effectiveSlots.filter(slot => statusFilter === "All" || (statusFilter === "Active" ? slot.isActive : !slot.isActive))
    .sort((a, b) => new Date(b.slotDate).getTime() - new Date(a.slotDate).getTime())
  const upcomingSlots = filteredSlots.filter(slot => !isPastSlot(slot))
  const pastSlots = filteredSlots.filter(isPastSlot)
  const totalSlots = slots.length
  const activeSlots = effectiveSlots.filter(slot => slot.isActive).length

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Slot Management
          </h1>
          <p className="text-muted-foreground">
            Manage energy capacity slots and operating schedules.
          </p>
        </div>

        {canManageSlots && (
          <AddSlotDialog
            onSlotAdded={(newSlot) => setSlots([newSlot, ...slots])}
          />
        )}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Total Slots</p>
          <p className="mt-2 text-2xl font-semibold">{totalSlots}</p>
        </div>

        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Active Slots</p>
          <p className="mt-2 text-2xl font-semibold text-green-600">
            {activeSlots}
          </p>
        </div>

        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Inactive Slots</p>
          <p className="mt-2 text-2xl font-semibold text-red-600">
            {totalSlots - activeSlots}
          </p>
        </div>
      </div>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <select
          value={stationFilter}
          onChange={handleStationFilterChange}
          className="rounded-lg border bg-background px-3 py-2 text-sm outline-none sm:w-80"
        >
          <option value="All">All Stations</option>
          {stations.map(station => (
            <option key={station.id} value={station.id}>
              {station.stationName}
            </option>
          ))}
        </select>

        <select
          value={statusFilter}
          onChange={(event) =>
            setStatusFilter(event.target.value as "All" | "Active" | "Inactive")
          }
          className="rounded-lg border bg-background px-3 py-2 text-sm outline-none"
        >
          <option value="All">All Statuses</option>
          <option value="Active">Active</option>
          <option value="Inactive">Inactive</option>
        </select>
      </div>

      {loading && (
        <p className="text-sm text-muted-foreground">Loading slots...</p>
      )}

      {!loading && error && (
        <p className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-500">
          {error}
        </p>
      )}

      {!loading && !error && (
        <div className="flex flex-col gap-8">
          <div>
            <h2 className="mb-4 text-lg font-medium">Upcoming / Current Slots</h2>
            <div className="overflow-x-auto rounded-xl border">
              <Table className="w-full text-left text-sm">
                <TableHeader className="bg-muted/50">
                  <TableRow className="border-b">
                    <TableHead className="px-4 py-3 font-medium">Station</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Date</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Time (Start - End)</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Capacity (kWh)</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Available (kWh)</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Status</TableHead>
                    {canManageSlots && (
                      <TableHead className="px-4 py-3 font-medium text-right">Actions</TableHead>
                    )}
                  </TableRow>
                </TableHeader>

                <TableBody>
                  {upcomingSlots.map((slot) => (
                    <TableRow
                      key={slot.id}
                      className="border-b transition-colors last:border-0 hover:bg-muted/30"
                    >
                      <TableCell className="px-4 py-3">{stationMap.get(slot.stationId) || slot.stationId}</TableCell>
                      <TableCell className="px-4 py-3">{new Date(slot.slotDate).toLocaleDateString()}</TableCell>
                      <TableCell className="px-4 py-3">{slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}</TableCell>
                      <TableCell className="px-4 py-3">{slot.energyCapacityKwh}</TableCell>
                      <TableCell className="px-4 py-3">{slot.availableEnergyKwh}</TableCell>
                      <TableCell className="px-4 py-3">
                        <StatusBadge status={slot.isActive ? "Active" : "Inactive"} />
                      </TableCell>
                      <TableCell className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-1">
                          {slot.isActive && (
                            <Button
                              variant="outline"
                              size="icon"
                              onClick={(e) => {
                                e.stopPropagation()
                                setEditingSlot(slot)
                              }}
                              title="Edit Slot"
                            >
                              <Pencil className="size-4" />
                              <span className="sr-only">Edit</span>
                            </Button>
                          )}
                          <Button
                            variant="outline"
                            size="icon"
                            onClick={(e) => handleToggleStatus(e, slot)}
                            title={
                              slot.isActive
                                ? "Deactivate Slot"
                                : "Reactivate Slot"
                            }
                          >
                            <Power
                              className={`size-4 ${slot.isActive ? "text-red-500" : "text-green-500"}`}
                            />
                            <span className="sr-only">
                              {slot.isActive ? "Deactivate" : "Reactivate"}
                            </span>
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}

                  {upcomingSlots.length === 0 && (
                    <TableRow>
                      <TableCell
                        colSpan={canManageSlots ? 7 : 6}
                        className="px-4 py-8 text-center text-muted-foreground"
                      >
                        No upcoming slots found.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          </div>

          <div>
            <h2 className="mb-4 text-lg font-medium">Past Slots</h2>
            <div className="overflow-x-auto rounded-xl border">
              <Table className="w-full text-left text-sm opacity-75">
                <TableHeader className="bg-muted/50">
                  <TableRow className="border-b">
                    <TableHead className="px-4 py-3 font-medium">Station</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Date</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Time (Start - End)</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Capacity (kWh)</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Available (kWh)</TableHead>
                    <TableHead className="px-4 py-3 font-medium">Status</TableHead>
                    {canManageSlots && (
                      <TableHead className="px-4 py-3 font-medium text-right">Actions</TableHead>
                    )}
                  </TableRow>
                </TableHeader>

                <TableBody>
                  {pastSlots.map((slot) => (
                    <TableRow
                      key={slot.id}
                      className="border-b transition-colors last:border-0 hover:bg-muted/30"
                    >
                      <TableCell className="px-4 py-3">{stationMap.get(slot.stationId) || slot.stationId}</TableCell>
                      <TableCell className="px-4 py-3">{new Date(slot.slotDate).toLocaleDateString()}</TableCell>
                      <TableCell className="px-4 py-3">{slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}</TableCell>
                      <TableCell className="px-4 py-3">{slot.energyCapacityKwh}</TableCell>
                      <TableCell className="px-4 py-3">{slot.availableEnergyKwh}</TableCell>
                      <TableCell className="px-4 py-3">
                        <StatusBadge status={slot.isActive ? "Active" : "Inactive"} />
                      </TableCell>
                      {canManageSlots && <TableCell className="text-right text-muted-foreground">Expired</TableCell>}
                    </TableRow>
                  ))}

                  {pastSlots.length === 0 && (
                    <TableRow>
                      <TableCell
                        colSpan={canManageSlots ? 7 : 6}
                        className="px-4 py-8 text-center text-muted-foreground"
                      >
                        No past slots found.
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </div>
          </div>
        </div>
      )}

      <EditSlotDialog
        slot={editingSlot}
        station={editingSlot ? stations.find(s => s.id === editingSlot.stationId) : null}
        open={editingSlot !== null}
        onOpenChange={(open) => !open && setEditingSlot(null)}
        onSlotUpdated={handleSlotUpdated}
      />

      <Dialog open={!!actionError} onOpenChange={(open) => !open && setActionError("")}>
        <DialogContent className="sm:max-w-[425px]">
          <DialogHeader>
            <DialogTitle>Unable to Deactivate Slot</DialogTitle>
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
