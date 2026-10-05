import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table"
import { StatusBadge } from "@/components/status-badge"
import { Input } from "@/components/ui/input"
import { useEffect, useState } from "react"
import {
  getReservations,
  updateReservationStatus,
} from "@/lib/reservation-api"
import type { EnergyReservation } from "@/types/reservation"
import { useAuth } from "@/context/AuthContext"
import { Button } from "@/components/ui/button"
import {
  Search,
  Calendar,
  Clock,
  Pencil,
  Ban,
  RefreshCw,
} from "lucide-react"
import { CreateReservationDialog } from "@/components/create-reservation-dialog"
import { CancelReservationDialog } from "@/components/cancel-reservation-dialog"
import { ModifyReservationDialog } from "@/components/modify-reservation-dialog"

export function ReservationsPage() {
  const { user } = useAuth()
  const canOperate = user?.role === "Backoffice" || user?.role === "Grid Operator"
  const [reservations, setReservations] = useState<EnergyReservation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [searchTerm, setSearchTerm] = useState("")
  const [statusFilter, setStatusFilter] = useState<
    "All" | "Pending" | "Approved" | "InProgress" | "Completed" | "Cancelled"
  >("All")

  // Modals
  const [cancelReservation, setCancelReservation] = useState<EnergyReservation | null>(null)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [modifyReservation, setModifyReservation] = useState<EnergyReservation | null>(null)
  const [modifyOpen, setModifyOpen] = useState(false)

  useEffect(() => {
    loadReservations()
  }, [])

  async function loadReservations() {
    try {
      setLoading(true)
      setError("")
      const data = await getReservations()
      setReservations(data)
    } catch {
      setError("Unable to load reservations. Make sure the API is running.")
    } finally {
      setLoading(false)
    }
  }

  // Quick Status Transition (Approve / Complete)
  async function handleStatusChange(
    id: string,
    newStatus: "Approved"
  ) {
    try {
      const updated = await updateReservationStatus(id, newStatus)
      setReservations((prev) =>
        prev.map((r) => (r.id === id ? updated : r))
      )
    } catch (err: unknown) {
      alert(err instanceof Error ? err.message : "Failed to update status")
    }
  }

  const filteredReservations = reservations
    .filter((res) => {
      const term = searchTerm.toLowerCase()
      const matchesSearch =
        (res.prosumerName ?? "").toLowerCase().includes(term) || res.prosumerNIC.toLowerCase().includes(term) ||
        res.id.toLowerCase().includes(term) ||
        (res.stationId && res.stationId.toLowerCase().includes(term))
      const matchesStatus =
        statusFilter === "All" || res.status === statusFilter
      return matchesSearch && matchesStatus
    })
    .sort(
      (a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    )

  // Metrics
  const totalCount = reservations.length
  const pendingCount = reservations.filter((r) => r.status === "Pending").length
  const approvedCount = reservations.filter((r) => r.status === "Approved").length
  const completedCount = reservations.filter((r) => r.status === "Completed").length
  const cancelledCount = reservations.filter((r) => r.status === "Cancelled").length

  return (
    <div className="flex-1 space-y-6 p-6">
      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Energy Reservations</h1>
          <p className="text-sm text-muted-foreground">
            Manage solar slot bookings, enforce 7-day and 12-hour rules, and track approval states.
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="icon"
            onClick={loadReservations}
            title="Refresh"
          >
            <RefreshCw className="h-4 w-4" />
          </Button>
          <CreateReservationDialog
            onReservationCreated={(newRes) => {
              setReservations((prev) => [newRes, ...prev])
            }}
          />
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <div className="rounded-xl border bg-card p-4 shadow-xs">
          <div className="text-xs font-medium text-muted-foreground">Total Bookings</div>
          <div className="mt-1 text-2xl font-bold">{totalCount}</div>
        </div>
        <div className="rounded-xl border bg-card p-4 shadow-xs">
          <div className="text-xs font-medium text-amber-600 dark:text-amber-400">Pending Review</div>
          <div className="mt-1 text-2xl font-bold text-amber-600 dark:text-amber-400">{pendingCount}</div>
        </div>
        <div className="rounded-xl border bg-card p-4 shadow-xs">
          <div className="text-xs font-medium text-blue-600 dark:text-blue-400">Approved</div>
          <div className="mt-1 text-2xl font-bold text-blue-600 dark:text-blue-400">{approvedCount}</div>
        </div>
        <div className="rounded-xl border bg-card p-4 shadow-xs">
          <div className="text-xs font-medium text-emerald-600 dark:text-emerald-400">Completed</div>
          <div className="mt-1 text-2xl font-bold text-emerald-600 dark:text-emerald-400">{completedCount}</div>
        </div>
        <div className="rounded-xl border bg-card p-4 shadow-xs">
          <div className="text-xs font-medium text-zinc-500">Cancelled</div>
          <div className="mt-1 text-2xl font-bold text-zinc-500">{cancelledCount}</div>
        </div>
      </div>

      {/* Filters & Search */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:w-72">
          <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
          <Input
            type="text"
            placeholder="Search by name or NIC..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex h-9 w-full rounded-md border border-input bg-transparent pl-8 pr-3 py-1 text-sm shadow-xs focus-visible:ring-ring focus-visible:outline-none"
          />
        </div>

        <div className="flex flex-wrap gap-1.5">
          {(["All", "Pending", "Approved", "InProgress", "Completed", "Cancelled"] as const).map((filter) => (
            <Button size="sm" variant={statusFilter === filter ? "default" : "outline"}
              key={filter}
              onClick={() => setStatusFilter(filter)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                statusFilter === filter
                  ? "bg-primary text-primary-foreground shadow-xs"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              {filter}
            </Button>
          ))}
        </div>
      </div>

      {/* Table / List */}
      <div className="rounded-xl border bg-card shadow-xs">
        {loading ? (
          <div className="p-8 text-center text-sm text-muted-foreground">
            Loading reservations...
          </div>
        ) : error ? (
          <div className="p-8 text-center text-sm text-red-500">
            {error}
          </div>
        ) : filteredReservations.length === 0 ? (
          <div className="p-12 text-center text-sm text-muted-foreground">
            No reservations found matching your criteria.
          </div>
        ) : (
          <div className="overflow-x-auto">
            <Table className="w-full text-left text-sm">
              <TableHeader className="border-b bg-muted/40 text-xs font-medium text-muted-foreground">
                <TableRow>
                                    <TableHead className="px-4 py-3">Prosumer</TableHead>
                  <TableHead className="px-4 py-3">Date & Slot</TableHead>
                  <TableHead className="px-4 py-3">Capacity</TableHead>
                  <TableHead className="px-4 py-3">Status</TableHead>
                  <TableHead className="px-4 py-3 text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody className="divide-y">
                {filteredReservations.map((res) => {
                  const isModifiable =
                    !res.transferId && (res.status === "Pending" || res.status === "Approved")

                  return (
                    <TableRow key={res.id} className="hover:bg-muted/30 transition-colors">
                      <TableCell className="px-4 py-3 font-medium">
                        <p>{res.prosumerName || "Name unavailable"}</p><p className="text-xs font-normal text-muted-foreground">{res.prosumerNIC}</p>
                      </TableCell>
                      <TableCell className="px-4 py-3">
                        <div className="flex flex-col text-xs">
                          <span className="flex items-center gap-1 font-medium">
                            <Calendar className="h-3 w-3 text-muted-foreground" />
                            {new Date(res.reservationDate).toLocaleDateString()}
                          </span>
                          <span className="flex items-center gap-1 text-muted-foreground">
                            <Clock className="h-3 w-3" />
                            {res.startTime} - {res.endTime}
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="px-4 py-3 font-semibold text-emerald-600 dark:text-emerald-400">
                        {res.requestedEnergyKwh} kWh
                      </TableCell>
                      <TableCell className="px-4 py-3">
                        <StatusBadge status={res.status} />
                      </TableCell>
                      <TableCell className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Approve Action */}
                          {canOperate && res.status === "Pending" && (
                            <Button
                              size="sm"
                              variant="outline"

                              onClick={() => handleStatusChange(res.id, "Approved")}
                              title="Approve Reservation"
                            >
                              Approve
                            </Button>
                          )}

                          {/* Complete Action (Member 4 demo) */}
                          {canOperate && (res.status === "Approved" || res.status === "InProgress") && (
                            <Button
                              size="sm"
                              variant="outline"

                              onClick={() => { window.location.href = "/transfers" }}
                              title="Open transfers to verify and complete"
                            >
                              Transfer
                            </Button>
                          )}

                          {/* Modify Action */}
                          {isModifiable && (
                            <Button
                              size="sm"
                              variant="outline"

                              onClick={() => {
                                setModifyReservation(res)
                                setModifyOpen(true)
                              }}
                              title="Modify Reservation"
                            >
                              <Pencil /> Edit
                            </Button>
                          )}

                          {/* Cancel Action */}
                          {isModifiable && (
                            <Button
                              size="sm"
                              variant="outline"

                              onClick={() => {
                                setCancelReservation(res)
                                setCancelOpen(true)
                              }}
                              title="Cancel Reservation"
                            >
                              <Ban /> Cancel
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </div>
        )}
      </div>

      {/* Dialogs */}
      <CancelReservationDialog
        reservation={cancelReservation}
        open={cancelOpen}
        onOpenChange={setCancelOpen}
        onCancelled={(cancelled) => {
          setReservations((prev) =>
            prev.map((r) => (r.id === cancelled.id ? cancelled : r))
          )
        }}
      />

      <ModifyReservationDialog
        reservation={modifyReservation}
        open={modifyOpen}
        onOpenChange={setModifyOpen}
        onUpdated={(updated) => {
          setReservations((prev) =>
            prev.map((r) => (r.id === updated.id ? updated : r))
          )
        }}
      />
    </div>
  )
}
