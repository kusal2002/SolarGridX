import { useState, useEffect } from "react"
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
import { updateSlot } from "@/lib/slot-api"
import type { Slot } from "@/types/slot"
import type { Station } from "@/types/station"

interface EditSlotDialogProps {
  slot: Slot | null
  station?: Station | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSlotUpdated: (slot: Slot) => void
}

// Dialog component for editing existing charging slot schedule and capacity
export function EditSlotDialog({
  slot,
  station,
  open,
  onOpenChange,
  onSlotUpdated,
}: EditSlotDialogProps) {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const [formData, setFormData] = useState({
    slotDate: "",
    startTime: "",
    endTime: "",
    energyCapacityKwh: "",
  })

  // Initialize form fields when selected slot changes
  useEffect(() => {
    if (slot) {
      // Parse the slot date to YYYY-MM-DD for the date input
      const dateStr = slot.slotDate.split('T')[0]
      
      // Parse time to HH:mm for the time input
      const startStr = slot.startTime.substring(0, 5)
      const endStr = slot.endTime.substring(0, 5)
      
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFormData({
        slotDate: dateStr,
        startTime: startStr,
        endTime: endStr,
        energyCapacityKwh: slot.energyCapacityKwh.toString(),
      })
      setError("")
    }
  }, [slot])

  // Revert form state back to original slot values
  const resetForm = () => {
    if (slot) {
      const dateStr = slot.slotDate.split('T')[0]
      const startStr = slot.startTime.substring(0, 5)
      const endStr = slot.endTime.substring(0, 5)
      
      setFormData({
        slotDate: dateStr,
        startTime: startStr,
        endTime: endStr,
        energyCapacityKwh: slot.energyCapacityKwh.toString(),
      })
    }
    setError("")
  }

  // Close dialog and discard edits
  const handleCancel = () => {
    resetForm()
    onOpenChange(false)
  }

  // Update form state on field change
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  // Validate timing against station operating hours and submit updates
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!slot) return
    
    setLoading(true)
    setError("")

    try {
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      
      const [year, month, day] = formData.slotDate.split('-').map(Number)
      const selectedDate = new Date(year, month - 1, day)
      selectedDate.setHours(0, 0, 0, 0)

      if (selectedDate < today) {
        setError("Slot date cannot be in the past.")
        setLoading(false)
        return
      }

      if (formData.startTime >= formData.endTime) {
        setError("Start time must be before end time.")
        setLoading(false)
        return
      }

      if (selectedDate.getTime() === today.getTime()) {
        const now = new Date()
        const currentHours = now.getHours().toString().padStart(2, "0")
        const currentMinutes = now.getMinutes().toString().padStart(2, "0")
        const currentTime = `${currentHours}:${currentMinutes}`

        if (formData.startTime <= currentTime) {
          setError("For today's date, the start time must be later than the current time.")
          setLoading(false)
          return
        }
      }

      if (station && (station.operatingStartTime || station.operatingEndTime)) {
        if (!station.operatingStartTime || !station.operatingEndTime || station.operatingStartTime >= station.operatingEndTime) {
          setError("Station operating schedule is invalid. Cannot update slot.")
          setLoading(false)
          return
        }

        const start = formData.startTime.length > 5 ? formData.startTime.slice(0, 5) : formData.startTime
        const end = formData.endTime.length > 5 ? formData.endTime.slice(0, 5) : formData.endTime
        const opStart = station.operatingStartTime.length > 5 ? station.operatingStartTime.slice(0, 5) : station.operatingStartTime
        const opEnd = station.operatingEndTime.length > 5 ? station.operatingEndTime.slice(0, 5) : station.operatingEndTime

        if (start < opStart || end > opEnd) {
          setError(`Slot times must fall within the station's operating hours (${opStart} - ${opEnd}).`)
          setLoading(false)
          return
        }
      }

      // Validate slot capacity does not exceed station total capacity
      if (station && parseFloat(formData.energyCapacityKwh) > station.totalCapacityKwh) {
        setError(`Energy capacity (${formData.energyCapacityKwh} kWh) cannot exceed the station's total capacity (${station.totalCapacityKwh} kWh).`)
        setLoading(false)
        return
      }

      const formatTime = (time: string) => {
        if (time.length === 5) return `${time}:00`
        return time
      }

      const updated = await updateSlot(slot.id, {
        slotDate: formData.slotDate,
        startTime: formatTime(formData.startTime),
        endTime: formatTime(formData.endTime),
        energyCapacityKwh: parseFloat(formData.energyCapacityKwh),
      })

      onSlotUpdated(updated)
      resetForm()
      onOpenChange(false)
    } catch (err: unknown) {
      console.error(err)
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError("Failed to update slot. Please try again.")
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Edit Energy Slot</DialogTitle>
            <DialogDescription>
              Modify the time or capacity of an existing energy slot.
            </DialogDescription>
          </DialogHeader>

          {error && (
            <p className="mt-4 rounded-md bg-red-50 p-2 text-sm text-red-500">
              {error}
            </p>
          )}

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label>Station</Label>
              <Input
                value={station?.stationName || slot?.stationId || ""}
                disabled
                className="bg-muted text-muted-foreground"
              />
              <p className="text-xs text-muted-foreground">Station cannot be changed.</p>
            </div>
            
            <div className="grid gap-2">
              <Label htmlFor="edit-slotDate">Slot Date</Label>
              <Input
                id="edit-slotDate"
                name="slotDate"
                type="date"
                min={new Date().toLocaleDateString('en-CA')}
                value={formData.slotDate}
                onChange={handleChange}
                required
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="edit-startTime">Start Time</Label>
                <Input
                  id="edit-startTime"
                  name="startTime"
                  type="time"
                  value={formData.startTime}
                  onChange={handleChange}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="edit-endTime">End Time</Label>
                <Input
                  id="edit-endTime"
                  name="endTime"
                  type="time"
                  value={formData.endTime}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="edit-energyCapacityKwh">Energy Capacity (kWh)</Label>
              <Input
                id="edit-energyCapacityKwh"
                name="energyCapacityKwh"
                type="number"
                step="any"
                min="0.1"
                placeholder="e.g. 100"
                value={formData.energyCapacityKwh}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              onClick={handleCancel}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={loading}>
              {loading ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
