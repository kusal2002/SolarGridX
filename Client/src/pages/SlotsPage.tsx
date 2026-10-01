import { useEffect, useState, useMemo } from "react"
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
  
  const [editingSlot, setEditingSlot] = useState<Slot | null>(null)
  
  const [stationFilter, setStationFilter] = useState<string>("All")
  const [statusFilter, setStatusFilter] = useState<"All" | "Active" | "Inactive">("All")

  const { user } = useAuth()
  const canManageSlots = user?.role === "Backoffice" || user?.role === "Grid Operator"
  const canViewInactive = canManageSlots

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true)
        setError("")
        
        // Fetch both stations and slots
        const [stationsData, slotsData] = await Promise.all([
          getStations(canViewInactive),
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
  }, [canViewInactive])

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
        alert(err.message || "Failed to change slot status.")
      } else {
        alert("Failed to change slot status.")
      }
    }
  }

  // Map station ID to station Name for easy display
  const stationMap = useMemo(() => {
    const map = new Map<string, string>()
    stations.forEach(s => map.set(s.id, s.stationName))
    return map
  }, [stations])

  const filteredSlots = slots
    .filter((slot) => {
      const matchesStatus =
        statusFilter === "All" ||
        (statusFilter === "Active" && slot.isActive) ||
        (statusFilter === "Inactive" && !slot.isActive)
      return matchesStatus
    })
    .sort(
      (a, b) =>
        new Date(b.slotDate).getTime() - new Date(a.slotDate).getTime()
    )

  const isPastSlot = (slot: Slot) => {
    const today = new Date()
    const slotDate = new Date(slot.slotDate)
    
    const todayYMD = new Date(today.getFullYear(), today.getMonth(), today.getDate())
    const slotDateYMD = new Date(slotDate.getFullYear(), slotDate.getMonth(), slotDate.getDate())

    if (slotDateYMD < todayYMD) {
      return true
    }
    
    if (slotDateYMD.getTime() === todayYMD.getTime()) {
      const nowHours = today.getHours().toString().padStart(2, "0")
      const nowMins = today.getMinutes().toString().padStart(2, "0")
      const currentTime = `${nowHours}:${nowMins}`
      
      const formattedEndTime = slot.endTime.substring(0, 5) // "HH:mm"
      
      if (currentTime >= formattedEndTime) {
        return true
      }
    }
    return false
  }

  const upcomingSlots = filteredSlots.filter(s => !isPastSlot(s))
  const pastSlots = filteredSlots.filter(s => isPastSlot(s))

  const totalSlots = slots.length
  const activeSlots = slots.filter((slot) => slot.isActive).length

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
              <table className="w-full text-left text-sm">
                <thead className="bg-muted/50">
                  <tr className="border-b">
                    <th className="px-4 py-3 font-medium">Slot ID</th>
                    <th className="px-4 py-3 font-medium">Station</th>
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium">Time (Start - End)</th>
                    <th className="px-4 py-3 font-medium">Capacity (kWh)</th>
                    <th className="px-4 py-3 font-medium">Available (kWh)</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    {canManageSlots && (
                      <th className="px-4 py-3 font-medium text-right">Actions</th>
                    )}
                  </tr>
                </thead>

                <tbody>
                  {upcomingSlots.map((slot) => (
                    <tr
                      key={slot.id}
                      className="border-b transition-colors last:border-0 hover:bg-muted/30"
                    >
                      <td className="px-4 py-3 font-medium">{slot.id}</td>
                      <td className="px-4 py-3">{stationMap.get(slot.stationId) || slot.stationId}</td>
                      <td className="px-4 py-3">{new Date(slot.slotDate).toLocaleDateString()}</td>
                      <td className="px-4 py-3">{slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}</td>
                      <td className="px-4 py-3">{slot.energyCapacityKwh}</td>
                      <td className="px-4 py-3">{slot.availableEnergyKwh}</td>
                      <td className="px-4 py-3">
                        <span
                          className={
                            slot.isActive
                              ? "rounded-full bg-green-100 px-2 py-1 text-xs font-medium text-green-700"
                              : "rounded-full bg-red-100 px-2 py-1 text-xs font-medium text-red-700"
                          }
                        >
                          {slot.isActive ? "Active" : "Inactive"}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex justify-end gap-1">
                          {slot.isActive && (
                            <Button
                              variant="ghost"
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
                            variant="ghost"
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
                      </td>
                    </tr>
                  ))}

                  {upcomingSlots.length === 0 && (
                    <tr>
                      <td
                        colSpan={canManageSlots ? 8 : 7}
                        className="px-4 py-8 text-center text-muted-foreground"
                      >
                        No upcoming slots found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div>
            <h2 className="mb-4 text-lg font-medium">Past Slots</h2>
            <div className="overflow-x-auto rounded-xl border">
              <table className="w-full text-left text-sm opacity-75">
                <thead className="bg-muted/50">
                  <tr className="border-b">
                    <th className="px-4 py-3 font-medium">Slot ID</th>
                    <th className="px-4 py-3 font-medium">Station</th>
                    <th className="px-4 py-3 font-medium">Date</th>
                    <th className="px-4 py-3 font-medium">Time (Start - End)</th>
                    <th className="px-4 py-3 font-medium">Capacity (kWh)</th>
                    <th className="px-4 py-3 font-medium">Available (kWh)</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    {canManageSlots && (
                      <th className="px-4 py-3 font-medium text-right">Actions</th>
                    )}
                  </tr>
                </thead>

                <tbody>
                  {pastSlots.map((slot) => (
                    <tr
                      key={slot.id}
                      className="border-b transition-colors last:border-0 hover:bg-muted/30"
                    >
                      <td className="px-4 py-3 font-medium">{slot.id}</td>
                      <td className="px-4 py-3">{stationMap.get(slot.stationId) || slot.stationId}</td>
                      <td className="px-4 py-3">{new Date(slot.slotDate).toLocaleDateString()}</td>
                      <td className="px-4 py-3">{slot.startTime.substring(0, 5)} - {slot.endTime.substring(0, 5)}</td>
                      <td className="px-4 py-3">{slot.energyCapacityKwh}</td>
                      <td className="px-4 py-3">{slot.availableEnergyKwh}</td>
                      <td className="px-4 py-3">
                        <span
                          className={
                            slot.isActive
                              ? "rounded-full bg-gray-100 px-2 py-1 text-xs font-medium text-gray-700"
                              : "rounded-full bg-red-100 px-2 py-1 text-xs font-medium text-red-700"
                          }
                        >
                          {slot.isActive ? "Active (Completed)" : "Inactive"}
                        </span>
                      </td>
                      {canManageSlots && (
                        <td className="px-4 py-3 text-right">
                          <div className="flex justify-end gap-1">
                            {slot.isActive && (
                              <Button
                                variant="ghost"
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
                              variant="ghost"
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
                        </td>
                      )}
                    </tr>
                  ))}

                  {pastSlots.length === 0 && (
                    <tr>
                      <td
                        colSpan={canManageSlots ? 8 : 7}
                        className="px-4 py-8 text-center text-muted-foreground"
                      >
                        No past slots found.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
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
    </div>
  )
}
