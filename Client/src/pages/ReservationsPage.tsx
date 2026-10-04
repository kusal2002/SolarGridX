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
  Zap,
  Calendar,
  Clock,
  CheckCircle2,
  XCircle,
  Hourglass,
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
        res.prosumerNIC.toLowerCase().includes(term) ||
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

  function getStatusBadge(status: EnergyReservation["status"]) {
    switch (status) {
      case "Pending":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-semibold text-amber-800 dark:bg-amber-950/60 dark:text-amber-300">
            <Hourglass className="h-3 w-3" />
            Pending
          </span>
        )
      case "Approved":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-blue-100 px-2.5 py-0.5 text-xs font-semibold text-blue-800 dark:bg-blue-950/60 dark:text-blue-300">
            <CheckCircle2 className="h-3 w-3" />
            Approved
          </span>
        )
      case "Completed":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-semibold text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-300">
            <Zap className="h-3 w-3" />
            Completed
          </span>
        )
      case "InProgress":
        return <span className="rounded-full bg-blue-100 px-2.5 py-0.5 text-xs text-blue-800">In progress</span>
      case "Cancelled":
        return (
          <span className="inline-flex items-center gap-1 rounded-full bg-zinc-100 px-2.5 py-0.5 text-xs font-semibold text-zinc-600 dark:bg-zinc-800 dark:text-zinc-400">
            <XCircle className="h-3 w-3" />
            Cancelled
          </span>
        )
    }
  }

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
          <input
            type="text"
            placeholder="Search by NIC, ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="flex h-9 w-full rounded-md border border-input bg-transparent pl-8 pr-3 py-1 text-sm shadow-xs focus-visible:ring-ring focus-visible:outline-none"
          />
        </div>

        <div className="flex flex-wrap gap-1.5">
          {(["All", "Pending", "Approved", "InProgress", "Completed", "Cancelled"] as const).map((filter) => (
            <button
              key={filter}
              onClick={() => setStatusFilter(filter)}
              className={`rounded-full px-3 py-1 text-xs font-medium transition-colors ${
                statusFilter === filter
                  ? "bg-primary text-primary-foreground shadow-xs"
                  : "bg-muted text-muted-foreground hover:bg-muted/80"
              }`}
            >
              {filter}
            </button>
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
            <table className="w-full text-left text-sm">
              <thead className="border-b bg-muted/40 text-xs font-medium text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Reservation ID</th>
                  <th className="px-4 py-3">Prosumer NIC</th>
                  <th className="px-4 py-3">Date & Slot</th>
                  <th className="px-4 py-3">Capacity</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {filteredReservations.map((res) => {
                  const isModifiable =
                    !res.transferId && (res.status === "Pending" || res.status === "Approved")

                  return (
                    <tr key={res.id} className="hover:bg-muted/30 transition-colors">
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">
                        {res.id.slice(-8)}
                      </td>
                      <td className="px-4 py-3 font-medium">
                        {res.prosumerNIC}
                      </td>
                      <td className="px-4 py-3">
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
                      </td>
                      <td className="px-4 py-3 font-semibold text-emerald-600 dark:text-emerald-400">
                        {res.requestedEnergyKwh} kWh
                      </td>
                      <td className="px-4 py-3">
                        {getStatusBadge(res.status)}
                      </td>
                      <td className="px-4 py-3 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {/* Approve Action */}
                          {canOperate && res.status === "Pending" && (
                            <Button
                              size="sm"
                              variant="outline"
                              className="h-7 text-xs text-blue-600 hover:text-blue-700"
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
                              className="h-7 text-xs text-emerald-600 hover:text-emerald-700"
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
                              variant="ghost"
                              className="h-7 w-7 p-0"
                              onClick={() => {
                                setModifyReservation(res)
                                setModifyOpen(true)
                              }}
                              title="Modify Reservation"
                            >
                              <Pencil className="h-3.5 w-3.5 text-muted-foreground" />
                            </Button>
                          )}

                          {/* Cancel Action */}
                          {isModifiable && (
                            <Button
                              size="sm"
                              variant="ghost"
                              className="h-7 w-7 p-0 text-red-500 hover:text-red-700"
                              onClick={() => {
                                setCancelReservation(res)
                                setCancelOpen(true)
                              }}
                              title="Cancel Reservation"
                            >
                              <Ban className="h-3.5 w-3.5" />
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
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
