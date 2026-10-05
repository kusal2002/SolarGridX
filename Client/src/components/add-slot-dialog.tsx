import { useState, useEffect } from "react"
import { Plus } from "lucide-react"
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
import { createSlot } from "@/lib/slot-api"
import { getStations } from "@/lib/station-api"
import type { Slot } from "@/types/slot"
import type { Station } from "@/types/station"

interface AddSlotDialogProps {
  onSlotAdded: (slot: Slot) => void
}

// Dialog component for creating a new charging slot for an active station
export function AddSlotDialog({ onSlotAdded }: AddSlotDialogProps) {
  const [open, setOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState("")

  const [stations, setStations] = useState<Station[]>([])
  const [loadingStations, setLoadingStations] = useState(false)

  // Form input state for slot creation
  const [formData, setFormData] = useState({
    stationId: "",
    slotDate: "",
    startTime: "",
    endTime: "",
    energyCapacityKwh: "",
  })

  // Fetch active stations when dialog is opened
  useEffect(() => {
    async function loadStations() {
      try {
        setLoadingStations(true)
        const data = await getStations()
        const activeStations = data.filter((s: Station) => s.isActive)
        setStations(activeStations)
        setFormData((prev) => {
          if (!prev.stationId && activeStations.length > 0) {
            return { ...prev, stationId: activeStations[0].id }
          }
          return prev
        })
      } catch (err) {
        console.error(err)
      } finally {
        setLoadingStations(false)
      }
    }

    if (open) {
      loadStations()
    }
  }, [open])

  // Reset form inputs and clear errors
  const resetForm = () => {
    setFormData({
      stationId: stations.length > 0 ? stations[0].id : "",
      slotDate: "",
      startTime: "",
      endTime: "",
      energyCapacityKwh: "",
    })
    setError("")
  }

  // Handle cancel click and close modal
  const handleCancel = () => {
    resetForm()
    setOpen(false)
  }

  // Update form state on field change
  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    const { name, value } = e.target
    setFormData((prev) => ({ ...prev, [name]: value }))
  }

  // Validate schedule constraints and submit slot to API
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setLoading(true)
    setError("")

    try {
      const today = new Date()
      today.setHours(0, 0, 0, 0)
      
      // Parse the slot date avoiding timezone shift by splitting
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

      const selectedStation = stations.find((s) => s.id === formData.stationId)
      if (selectedStation && (selectedStation.operatingStartTime || selectedStation.operatingEndTime)) {
        if (!selectedStation.operatingStartTime || !selectedStation.operatingEndTime || selectedStation.operatingStartTime >= selectedStation.operatingEndTime) {
          setError("Station operating schedule is invalid. Cannot create slot.")
          setLoading(false)
          return
        }

        const start = formData.startTime.length > 5 ? formData.startTime.slice(0, 5) : formData.startTime
        const end = formData.endTime.length > 5 ? formData.endTime.slice(0, 5) : formData.endTime
        const opStart = selectedStation.operatingStartTime.length > 5 ? selectedStation.operatingStartTime.slice(0, 5) : selectedStation.operatingStartTime
        const opEnd = selectedStation.operatingEndTime.length > 5 ? selectedStation.operatingEndTime.slice(0, 5) : selectedStation.operatingEndTime

        if (start < opStart || end > opEnd) {
          setError(`Slot times must fall within the station's operating hours (${opStart} - ${opEnd}).`)
          setLoading(false)
          return
        }
      }

      // Validate slot capacity does not exceed station total capacity
      if (selectedStation && parseFloat(formData.energyCapacityKwh) > selectedStation.totalCapacityKwh) {
        setError(`Energy capacity (${formData.energyCapacityKwh} kWh) cannot exceed the station's total capacity (${selectedStation.totalCapacityKwh} kWh).`)
        setLoading(false)
        return
      }

      // Backend expects TimeSpan format like "HH:mm:ss"
      const formatTime = (time: string) => {
        if (time.length === 5) return `${time}:00`
        return time
      }

      const newSlot = await createSlot({
        stationId: formData.stationId,
        slotDate: formData.slotDate,
        startTime: formatTime(formData.startTime),
        endTime: formatTime(formData.endTime),
        energyCapacityKwh: parseFloat(formData.energyCapacityKwh),
      })

      onSlotAdded(newSlot)
      resetForm()
      setOpen(false)
    } catch (err: unknown) {
      console.error(err)
      if (err instanceof Error) {
        setError(err.message)
      } else {
        setError("Failed to create slot. Please try again.")
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button />}>
        <Plus className="mr-2 size-4" />
        Add Slot
      </DialogTrigger>
      <DialogContent className="sm:max-w-[500px]">
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Add New Energy Slot</DialogTitle>
            <DialogDescription>
              Create a new available energy slot for a station.
            </DialogDescription>
          </DialogHeader>

          {error && (
            <p className="mt-4 rounded-md bg-red-50 p-2 text-sm text-red-500">
              {error}
            </p>
          )}

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="stationId">Station</Label>
              <select
                id="stationId"
                name="stationId"
                value={formData.stationId}
                onChange={handleChange}
                required
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <option value="" disabled>
                  {loadingStations ? "Loading stations..." : "Select a station"}
                </option>
                {stations.map((station) => (
                  <option key={station.id} value={station.id}>
                    {station.stationName} ({station.location})
                  </option>
                ))}
              </select>
            </div>
            
            <div className="grid gap-2">
              <Label htmlFor="slotDate">Slot Date</Label>
              <Input
                id="slotDate"
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
                <Label htmlFor="startTime">Start Time</Label>
                <Input
                  id="startTime"
                  name="startTime"
                  type="time"
                  value={formData.startTime}
                  onChange={handleChange}
                  required
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="endTime">End Time</Label>
                <Input
                  id="endTime"
                  name="endTime"
                  type="time"
                  value={formData.endTime}
                  onChange={handleChange}
                  required
                />
              </div>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="energyCapacityKwh">Energy Capacity (kWh)</Label>
              <Input
                id="energyCapacityKwh"
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
            <Button type="submit" disabled={loading || loadingStations}>
              {loading ? "Adding..." : "Add Slot"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
