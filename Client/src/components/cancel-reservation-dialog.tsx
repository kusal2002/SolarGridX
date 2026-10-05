import { useState, useEffect } from "react"
import { AlertTriangle, Clock } from "lucide-react"
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
import { cancelReservation } from "@/lib/reservation-api"
import type { EnergyReservation } from "@/types/reservation"

interface CancelReservationDialogProps {
  reservation: EnergyReservation | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onCancelled: (reservation: EnergyReservation) => void
}

// Confirmation dialog for cancelling a reservation with an optional reason
export function CancelReservationDialog({
  reservation,
  open,
  onOpenChange,
  onCancelled,
}: CancelReservationDialogProps) {
  const [reason, setReason] = useState("")
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    if (open) {
      setTimeout(() => setNow(Date.now()), 0)
    }
  }, [open])

  if (!reservation) return null

  // Calculate hours remaining until slot start to enforce the 12-hour cancellation policy
  const reservationDateTime = new Date(
    `${reservation.reservationDate.split("T")[0]}T${reservation.startTime}`
  )
  const hoursRemaining = (reservationDateTime.getTime() - now) / (1000 * 60 * 60)
  const isWithin12Hours = hoursRemaining < 12

  // Calls cancellation API with optional reason and updates parent state
  async function handleConfirmCancel() {
    if (!reservation) return
    try {
      setLoading(true)
      setError("")
      const result = await cancelReservation(reservation.id, reason)
      onCancelled(result)
      onOpenChange(false)
      setReason("")
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : "Failed to cancel reservation."
      setError(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2 text-red-600 dark:text-red-400">
            <AlertTriangle className="h-5 w-5" />
            Cancel Energy Reservation
          </DialogTitle>
          <DialogDescription>
            Are you sure you want to cancel this reservation? The reserved energy will be restored to the station slot.
          </DialogDescription>
        </DialogHeader>

        {isWithin12Hours ? (
          <div className="rounded-lg border border-red-200 bg-red-50 p-3.5 text-xs text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300">
            <div className="flex items-center gap-1.5 font-semibold">
              <Clock className="h-4 w-4" />
              12-Hour Cancellation Rule Enforced
            </div>
            <p className="mt-1">
              This reservation starts in {hoursRemaining.toFixed(1)} hours. The system strictly prohibits cancellations made less than 12 hours before the scheduled time.
            </p>
          </div>
        ) : (
          <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-800 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-300">
            <Clock className="mb-1 inline-block h-3.5 w-3.5 mr-1" />
            Slot starts in {hoursRemaining.toFixed(1)} hours (eligible for cancellation).
          </div>
        )}

        {error && (
          <div className="rounded-lg bg-red-100 p-2.5 text-xs text-red-700 dark:bg-red-950/50 dark:text-red-400">
            {error}
          </div>
        )}

        <div className="space-y-3 py-2 text-sm">
          <div className="flex justify-between border-b pb-2 text-xs">
            <span className="text-muted-foreground">Reservation ID:</span>
            <span className="font-mono">{reservation.id}</span>
          </div>
          <div className="flex justify-between border-b pb-2 text-xs">
            <span className="text-muted-foreground">Prosumer NIC:</span>
            <span className="font-medium">{reservation.prosumerNIC}</span>
          </div>
          <div className="flex justify-between border-b pb-2 text-xs">
            <span className="text-muted-foreground">Energy Reserved:</span>
            <span className="font-medium text-emerald-600 dark:text-emerald-400">
              {reservation.requestedEnergyKwh} kWh
            </span>
          </div>

          <div className="grid gap-1.5 pt-2">
            <Label htmlFor="cancelReason" className="text-xs">
              Reason for Cancellation (Optional)
            </Label>
            <Input
              id="cancelReason"
              placeholder="e.g. Schedule changed, no longer required"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              disabled={isWithin12Hours || loading}
            />
          </div>
        </div>

        <DialogFooter className="gap-2 sm:justify-end">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={loading}
          >
            Go Back
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={handleConfirmCancel}
            disabled={isWithin12Hours || loading}
          >
            {loading ? "Cancelling..." : "Confirm Cancellation"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
