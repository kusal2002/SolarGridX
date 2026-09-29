import { useState, useEffect } from "react"
import { Pencil, Clock, AlertTriangle, Zap, Calendar } from "lucide-react"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { updateReservation, getActiveSlots } from "@/lib/reservation-api"
import type { EnergyReservation, EnergySlot } from "@/types/reservation"

interface ModifyReservationDialogProps {
  reservation: EnergyReservation | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onUpdated: (reservation: EnergyReservation) => void
}

export function ModifyReservationDialog({
  reservation,
  open,
  onOpenChange,
  onUpdated,
}: ModifyReservationDialogProps) {
  const [newKwh, setNewKwh] = useState<number | "">("")
  const [newSlotId, setNewSlotId] = useState("")
  const [slots, setSlots] = useState<EnergySlot[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  useEffect(() => {
    if (reservation && open) {
      setNewKwh(reservation.requestedEnergyKwh)
      setNewSlotId(reservation.slotId)
      setError("")
      loadSlots()
    }
  }, [reservation, open])

  async function loadSlots() {
    try {
      const allSlots = await getActiveSlots()
      if (reservation) {
        setSlots(allSlots.filter((s) => s.stationId === reservation.stationId))
      }
    } catch {
      // Ignore slot load error if fallback
    }
  }

  if (!reservation) return null

  // 12-Hour check
  const reservationDateTime = new Date(
    `${reservation.reservationDate.split("T")[0]}T${reservation.startTime}`
  )
  const hoursRemaining = (reservationDateTime.getTime() - Date.now()) / (1000 * 60 * 60)
  const isWithin12Hours = hoursRemaining < 12

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!reservation) return
    if (typeof newKwh !== "number" || newKwh <= 0) {
      setError("Please enter a valid energy quantity.")
      return
    }

    try {
      setLoading(true)
      setError("")
      const updated = await updateReservation(reservation.id, {
        requestedEnergyKwh: newKwh,
        newSlotId: newSlotId !== reservation.slotId ? newSlotId : undefined,
      })
      onUpdated(updated)
      onOpenChange(false)
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to modify reservation."
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Pencil className="h-4 w-4" />
              Modify Energy Reservation
            </DialogTitle>
            <DialogDescription>
              Adjust your requested energy capacity or swap to another available slot.
            </DialogDescription>
          </DialogHeader>

          {isWithin12Hours ? (
            <div className="mt-3 rounded-lg border border-red-200 bg-red-50 p-3 text-xs text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300">
              <div className="flex items-center gap-1.5 font-semibold">
                <AlertTriangle className="h-4 w-4" />
                Modification Locked (&lt; 12 Hours)
              </div>
              <p className="mt-1">
                This reservation begins in {hoursRemaining.toFixed(1)} hours. Modifications are restricted within 12 hours of the slot start time.
              </p>
            </div>
          ) : (
            <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 p-2.5 text-xs text-emerald-800 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300">
              <Clock className="mb-0.5 inline-block h-3.5 w-3.5 mr-1" />
              Eligible for modification ({hoursRemaining.toFixed(1)} hours until start).
            </div>
          )}

          {error && (
            <div className="mt-2 rounded-lg bg-red-100 p-2.5 text-xs text-red-700 dark:bg-red-950/50 dark:text-red-400">
              {error}
            </div>
          )}

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="modifyKwh">Requested Energy (kWh)</Label>
              <Input
                id="modifyKwh"
                type="number"
                step="0.1"
                min="0.1"
                value={newKwh}
                onChange={(e) =>
                  setNewKwh(e.target.value === "" ? "" : Number(e.target.value))
                }
                disabled={isWithin12Hours || loading}
                required
              />
            </div>

            {slots.length > 1 && (
              <div className="grid gap-2">
                <Label htmlFor="swapSlot">Change Slot (Same Station)</Label>
                <select
                  id="swapSlot"
                  className="flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-xs focus-visible:ring-ring focus-visible:outline-none"
                  value={newSlotId}
                  onChange={(e) => setNewSlotId(e.target.value)}
                  disabled={isWithin12Hours || loading}
                >
                  {slots.map((s) => (
                    <option key={s.id} value={s.id} className="dark:bg-zinc-900">
                      {new Date(s.slotDate).toLocaleDateString()} ({s.startTime} - {s.endTime}) — {s.availableEnergyKwh} kWh free
                    </option>
                  ))}
                </select>
              </div>
            )}
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={loading}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isWithin12Hours || loading || typeof newKwh !== "number"}
            >
              {loading ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
