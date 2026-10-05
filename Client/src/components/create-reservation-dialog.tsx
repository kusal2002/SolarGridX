import { useState, useEffect } from "react"
import { Plus, Calendar, Clock, Zap, AlertCircle } from "lucide-react"
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
import { createReservation, getActiveSlots } from "@/lib/reservation-api"
import { getStations } from "@/lib/station-api"
import type { EnergyReservation, EnergySlot } from "@/types/reservation"
import type { Station } from "@/types/station"

interface CreateReservationDialogProps {
  onReservationCreated: (reservation: EnergyReservation) => void
}

export function CreateReservationDialog({
  onReservationCreated,
}: CreateReservationDialogProps) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const [stations, setStations] = useState<Station[]>([])
  const [slots, setSlots] = useState<EnergySlot[]>([])
  const [selectedStationId, setSelectedStationId] = useState("")
  const [selectedSlotId, setSelectedSlotId] = useState("")
  const [prosumerNIC, setProsumerNIC] = useState("")
  const [requestedKwh, setRequestedKwh] = useState<number | "">("")

  useEffect(() => {
    async function loadData() {
      try {
        setLoading(true)
        setError("")
        const [stationList, slotList] = await Promise.all([
          getStations().catch(() => []),
          getActiveSlots().catch(() => []),
        ])
        setStations(stationList)
        setSlots(slotList)
        
        // Auto-select first station that has active unexpired slots, or first active station
        const now = new Date()
        const nowMidnight = new Date()
        nowMidnight.setHours(0, 0, 0, 0)
        const sevenDaysLater = new Date(nowMidnight)
        sevenDaysLater.setDate(sevenDaysLater.getDate() + 7)

        const stationWithSlots = stationList.find((st: Station) =>
          slotList.some((sl) => {
            if (!sl.isActive || (sl.availableEnergyKwh ?? 0) <= 0 || sl.stationId !== st.id) return false
            const d = new Date(sl.slotDate)
            if (d < nowMidnight || d > sevenDaysLater) return false
            const [endHours, endMinutes] = (sl.endTime || "00:00").split(":").map(Number)
            const slotEndDateTime = new Date(sl.slotDate)
            slotEndDateTime.setHours(endHours || 0, endMinutes || 0, 0, 0)
            return slotEndDateTime > now
          })
        )

        if (stationWithSlots) {
          setSelectedStationId(stationWithSlots.id)
        } else if (stationList.length > 0) {
          setSelectedStationId(stationList[0].id)
        }
      } catch {
        setError("Failed to load stations or slots.")
      } finally {
        setLoading(false)
      }
    }

    if (open) {
      loadData()
    }
  }, [open])

  // Filter slots for selected station and within 7-day booking window (unexpired only)
  const now = new Date()
  const nowMidnight = new Date()
  nowMidnight.setHours(0, 0, 0, 0)
  const sevenDaysLater = new Date(nowMidnight)
  sevenDaysLater.setDate(sevenDaysLater.getDate() + 7)

  const availableStationSlots = slots.filter((slot) => {
    if (!slot.isActive || (slot.availableEnergyKwh ?? 0) <= 0 || slot.stationId !== selectedStationId) return false
    const slotDate = new Date(slot.slotDate)
    if (slotDate < nowMidnight || slotDate > sevenDaysLater) return false

    // Check if slot has expired
    const [endHours, endMinutes] = (slot.endTime || "00:00").split(":").map(Number)
    const slotEndDateTime = new Date(slot.slotDate)
    slotEndDateTime.setHours(endHours || 0, endMinutes || 0, 0, 0)
    if (slotEndDateTime <= now) return false

    return true
  })

  const selectedSlot = slots.find((s) => s.id === selectedSlotId)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!prosumerNIC.trim()) {
      setError("Please enter your Prosumer NIC.")
      return
    }
    if (!selectedSlotId) {
      setError("Please select an available energy slot.")
      return
    }
    if (typeof requestedKwh !== "number" || requestedKwh <= 0) {
      setError("Please enter a valid energy quantity in kWh.")
      return
    }
    if (selectedSlot && requestedKwh > selectedSlot.availableEnergyKwh) {
      setError(
        `Requested energy cannot exceed available capacity (${selectedSlot.availableEnergyKwh} kWh).`
      )
      return
    }

    try {
      setLoading(true)
      setError("")
      const created = await createReservation({
        prosumerNIC: prosumerNIC.trim(),
        slotId: selectedSlotId,
        requestedEnergyKwh: requestedKwh,
      })
      onReservationCreated(created)
      setOpen(false)
      resetForm()
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to create reservation."
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  function resetForm() {
    setSelectedSlotId("")
    setProsumerNIC("")
    setRequestedKwh("")
    setError("")
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button className="gap-2" />}>
        <Plus className="h-4 w-4" />
        Book Energy Slot
      </DialogTrigger>
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Book Energy Reservation</DialogTitle>
            <DialogDescription>
              Reserve energy from an active solar charging station within the 7-day booking window.
            </DialogDescription>
          </DialogHeader>

          {error && (
            <div className="mt-3 flex items-center gap-2 rounded-lg bg-red-50 p-3 text-sm text-red-600 dark:bg-red-950/40 dark:text-red-400">
              <AlertCircle className="h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
          )}

          <div className="grid gap-4 py-4">
            {/* Prosumer NIC */}
            <div className="grid gap-2">
              <Label htmlFor="nic">Prosumer NIC</Label>
              <Input
                id="nic"
                placeholder="e.g. 200012345678"
                value={prosumerNIC}
                onChange={(e) => setProsumerNIC(e.target.value)}
                required
              />
            </div>

            {/* Select Solar Station */}
            <div className="grid gap-2">
              <Label htmlFor="station">Solar Station</Label>
              <select
                id="station"
                className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs transition-[color,box-shadow] focus-visible:ring-[3px] focus-visible:ring-ring focus-visible:outline-none"
                value={selectedStationId}
                onChange={(e) => {
                  setSelectedStationId(e.target.value)
                  setSelectedSlotId("")
                }}
              >
                <option value="">-- Choose a Solar Station --</option>
                {stations.map((st) => {
                  const stationSlotCount = slots.filter(
                    (sl) => sl.stationId === st.id && sl.isActive
                  ).length
                  return (
                    <option key={st.id} value={st.id} className="dark:bg-zinc-900">
                      {st.stationName} — {st.location} {!st.isActive ? "(Inactive)" : stationSlotCount > 0 ? `(${stationSlotCount} slots)` : ""}
                    </option>
                  )
                })}
              </select>
            </div>

            {/* Select Slot */}
            <div className="grid gap-2">
              <div className="flex items-center justify-between">
                <Label htmlFor="slot">Available Energy Slot (Next 7 Days)</Label>
                <span className="text-xs text-muted-foreground">
                  {availableStationSlots.length} available
                </span>
              </div>

              {availableStationSlots.length === 0 ? (
                <div className="rounded-md border border-dashed p-4 text-center text-xs text-muted-foreground">
                  No slots currently available for this station within the 7-day booking window.
                </div>
              ) : (
                <div className="max-h-48 space-y-2 overflow-y-auto pr-1">
                  {availableStationSlots.map((slot) => {
                    const isSelected = selectedSlotId === slot.id
                    return (
                      <div
                        key={slot.id}
                        onClick={() => setSelectedSlotId(slot.id)}
                        className={`flex cursor-pointer items-center justify-between rounded-lg border p-3 text-sm transition-colors ${
                          isSelected
                            ? "border-primary bg-primary/5 ring-1 ring-primary"
                            : "border-border hover:bg-accent"
                        }`}
                      >
                        <div className="space-y-1">
                          <div className="flex items-center gap-1.5 font-medium">
                            <Calendar className="h-3.5 w-3.5 text-muted-foreground" />
                            {new Date(slot.slotDate).toLocaleDateString()}
                          </div>
                          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
                            <Clock className="h-3.5 w-3.5" />
                            {slot.startTime} - {slot.endTime}
                          </div>
                        </div>

                        <div className="text-right">
                          <span className="inline-flex items-center gap-1 text-xs font-semibold text-emerald-600 dark:text-emerald-400">
                            <Zap className="h-3 w-3" />
                            {slot.availableEnergyKwh} kWh free
                          </span>
                          <p className="text-[10px] text-muted-foreground">
                            Cap: {slot.energyCapacityKwh} kWh
                          </p>
                        </div>
                      </div>
                    )
                  })}
                </div>
              )}
            </div>

            {/* Requested kWh */}
            <div className="grid gap-2">
              <Label htmlFor="kwh">Requested Energy (kWh)</Label>
              <Input
                id="kwh"
                type="number"
                step="0.1"
                min="0.1"
                max={selectedSlot ? selectedSlot.availableEnergyKwh : 1000}
                placeholder="e.g. 15.0"
                value={requestedKwh}
                onChange={(e) =>
                  setRequestedKwh(e.target.value === "" ? "" : Number(e.target.value))
                }
                required
              />
              {selectedSlot && (
                <p className="text-xs text-muted-foreground">
                  Maximum available for this slot: {selectedSlot.availableEnergyKwh} kWh
                </p>
              )}
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => setOpen(false)}
              disabled={loading}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={loading || !selectedSlotId || !prosumerNIC || !requestedKwh}
            >
              {loading ? "Confirming..." : "Confirm Reservation"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
