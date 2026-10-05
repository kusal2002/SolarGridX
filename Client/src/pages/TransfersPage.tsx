import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { StatusBadge } from "@/components/status-badge"
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog"
import { Eye, ScanLine, RefreshCw, Clock3, Activity, CalendarCheck2, CheckCircle2, CircleOff } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { request } from "@/lib/api-client"
import type { EnergyReservation } from "@/types/reservation"
import { QrScanner } from "@/components/qr-scanner"

type Transfer = {
  id: string; reservationId: string; status: string; expectedEnergyKWh: number
  prosumerNIC: string; stationId: string; slotId: string
  transferredEnergyKWh: number; verifiedBy?: string; verifiedAt?: string
  completedAt?: string
  history: { action: string; status: string; actorNIC: string; at: string }[]
}
type Summary = { pending: number; current: number; approvedFuture: number; completed: number; cancelled: number }
const field = "rounded-md border bg-background p-2 text-sm"
const summaryIcons = { pending: Clock3, current: Activity, approvedFuture: CalendarCheck2, completed: CheckCircle2, cancelled: CircleOff }

// Energy transfers page for QR scanning, tracking charging sessions, meter progress, and completions
export function TransfersPage() {
  // State for metrics summary, current view tab, search, and pagination
  const [summary, setSummary] = useState<Summary | null>(null)
  const [view, setView] = useState("current")
  const [search, setSearch] = useState("")
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)

  // Selected booking and associated transfer details
  const [bookings, setBookings] = useState<EnergyReservation[]>([])
  const [selected, setSelected] = useState<EnergyReservation | null>(null)
  const [transfer, setTransfer] = useState<Transfer | null>(null)
  const [scanning, setScanning] = useState(false)
  const [transferLoaded, setTransferLoaded] = useState(false)
  const [energy, setEnergy] = useState("")
  const [reason, setReason] = useState("")
  const [message, setMessage] = useState("")
  const [busy, setBusy] = useState(false)

  // Fetch summary metrics and bookings list for current view and search query
  const load = useCallback(async () => {
    const [counts, result] = await Promise.all([
      request<Summary>("/dashboard/summary"),
      request<{ items: EnergyReservation[]; total: number }>(`/bookings/${view}?page=${page}&pageSize=20&search=${encodeURIComponent(search)}`),
    ])
    setSummary(counts); setBookings(result.items); setTotal(result.total)
  }, [view, page, search])

  // Debounced effect to reload data when filters or pagination change
  useEffect(() => { let active = true
    const timer = window.setTimeout(() => { void load().catch(e => { if (active) setMessage(e.message) }) }, 250)
    return () => { active = false; window.clearTimeout(timer) }
  }, [load])

  // Helper runner to manage busy loading states and error notifications
  async function run(operation: () => Promise<void>, refresh = true) {
    setBusy(true); setMessage("")
    try { await operation(); if (refresh) await load() }
    catch (e) { setMessage(e instanceof Error && e.message === "Failed to fetch"
      ? "Cannot reach the API. Check the backend is running and its URL/CORS settings, then refresh."
      : e instanceof Error ? e.message : "Request failed") }
    finally { setBusy(false) }
  }

  // Open the transfer details dialog for a selected booking
  async function open(booking: EnergyReservation) {
    setSelected(booking); setTransfer(null); setTransferLoaded(false); setReason("")
    await run(async () => {
      const list = await request<Transfer[]>(`/energy-transfers?reservationId=${booking.id}`)
      setTransfer(list[0] ?? null)
      setTransferLoaded(true)
      setEnergy(String(list[0]?.transferredEnergyKWh ?? 0))
    })
  }

  // Verify scanned QR token payload and load the corresponding transfer
  async function verify(payload: string) {
    setScanning(false)
    setTransfer(null)
    setSelected(null); setTransferLoaded(false)
    await run(async () => {
      const result = await request<Transfer>("/energy-transfers/verify", {
        method: "POST", body: JSON.stringify({ payload: payload.trim() }),
      })
      setTransfer(result); setEnergy(String(result.transferredEnergyKWh))
      setSelected(await request<EnergyReservation>(`/Reservation/${result.reservationId}`))
      setTransferLoaded(true); setMessage(`QR verified. Prosumer NIC: ${result.prosumerNIC}. Transfer is ready to start.`)
    })
  }

  // Execute transfer actions: start, record progress reading, complete, or fail
  async function change(action: string) {
    if (!transfer) return
    await run(async () => {
      const body = action === "progress" || action === "complete"
        ? { transferredEnergyKWh: Number(energy) } : { reason }
      const result = await request<Transfer>(`/energy-transfers/${transfer.id}/${action}`, {
        method: "PATCH", ...(action === "start" ? {} : { body: JSON.stringify(body) }),
      })
      setTransfer(result)
      if (action === "complete") { setView("completed"); setPage(1); setSearch("") }
      setSelected(await request<EnergyReservation>(`/Reservation/${result.reservationId}`))
      setMessage(action === "complete" ? "Transfer completed successfully." : `Transfer ${result.status}.`)
    }, action !== "complete")
  }

  return <main className="space-y-6 p-6">
    <h1 className="text-2xl font-semibold">Transfers & monitoring</h1>
    <p className="text-sm text-muted-foreground">Reservation approval accepts the booking. Scan its QR to verify the prosumer before starting energy delivery.</p>
    <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
      {summary && Object.entries(summary).filter(([key]) => ["pending", "current", "approvedFuture", "completed", "cancelled"].includes(key)).map(([key, count]) => <div key={key} className="rounded-lg border p-4">
        <div className="mb-2 flex items-center gap-2 text-muted-foreground">{(() => { const Icon = summaryIcons[key as keyof typeof summaryIcons]; return <Icon className="size-4" /> })()}<p className="text-sm text-muted-foreground">{key === "approvedFuture" ? "Approved future" : key}</p></div>
        <p className="text-2xl font-semibold">{count}</p>
      </div>)}
    </div>
    <div className="flex flex-wrap gap-2">
      <select aria-label="Booking view" className={field} value={view} onChange={e => { setView(e.target.value); setPage(1) }}>
        {[["current", "Current"], ["pending", "Pending"], ["completed", "Completed transfers"], ["history", "Completed & cancelled history"], ["search", "All bookings"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      <Input aria-label="Search bookings" className={field} placeholder="Prosumer NIC or station" value={search} onChange={e => { setSearch(e.target.value); setPage(1) }} />
      <Button variant="outline" disabled={busy} onClick={() => void run(load)}><RefreshCw /> Refresh</Button>
    </div>
    <p role="status" className="text-sm">{message}</p>
    <div className="overflow-x-auto rounded-lg border">
      <Table className="w-full text-left text-sm"><TableHeader><TableRow className="border-b bg-muted">
        {["Prosumer", "Station", "Date & time", "Energy", "Booking status", "Actions"].map((title, i) => <TableHead className="p-3" key={i}>{title}</TableHead>)}
      </TableRow></TableHeader><TableBody>{bookings.map(b => <TableRow key={b.id} className="border-b">
        <TableCell className="p-3"><p className="font-medium">{b.prosumerName || "Name unavailable"}</p><p className="text-xs text-muted-foreground">{b.prosumerNIC}</p></TableCell><TableCell className="p-3">{b.stationName || "Station unavailable"}</TableCell>
        <TableCell className="p-3">{new Date(b.reservationDate).toLocaleDateString()} {b.startTime}</TableCell>
        <TableCell className="p-3">{b.requestedEnergyKwh} kWh</TableCell><TableCell className="p-3"><StatusBadge status={b.status} /></TableCell>
        <TableCell className="p-3"><Button variant="outline" disabled={busy} onClick={() => void open(b)} size="sm"><Eye /> Details</Button></TableCell>
      </TableRow>)}</TableBody></Table>
      {!bookings.length && <p className="p-4">No bookings found.</p>}
    </div>
    <div className="flex items-center gap-3">
      <Button variant="outline" disabled={page === 1 || busy} onClick={() => setPage(page - 1)}>Previous</Button>
      <span>Page {page} · {total} bookings</span>
      <Button variant="outline" disabled={page * 20 >= total || busy} onClick={() => setPage(page + 1)}>Next</Button>
    </div>
    <section className="space-y-3 rounded-lg border p-4">
      <h2 className="text-lg font-semibold">Verify a transaction</h2>
      <p className="text-sm text-muted-foreground">Scan the prosumer’s approved booking QR. The server identifies the prosumer, station, slot, and reserved energy automatically.</p>
      <Button variant="outline" disabled={busy || scanning} onClick={() => setScanning(true)}><ScanLine /> Scan QR</Button>
      {scanning && <QrScanner onScan={verify} onClose={() => setScanning(false)} />}
    </section>
    <Dialog open={selected !== null} onOpenChange={value => { if (!value) setSelected(null) }}><DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl">
      <DialogHeader><DialogTitle>Transfer details</DialogTitle><DialogDescription>Booking, delivery readings, and transfer history.</DialogDescription></DialogHeader>
      {selected && <div className="space-y-4">
      {message && <p role="status" className="rounded-lg border bg-muted p-3 text-sm">{message}</p>}
      <div className="flex items-center justify-between gap-3"><div><p className="font-semibold">{selected.prosumerName || "Name unavailable"}</p><p className="text-sm text-muted-foreground">{selected.prosumerNIC}</p></div><StatusBadge status={selected.status} /></div>
      <p className="text-muted-foreground">{selected.stationName || "Station unavailable"}</p>
      <p>{selected.startTime}–{selected.endTime} · {selected.requestedEnergyKwh} kWh</p>
      {!transfer && <p>{busy ? "Loading transfer details…" : transferLoaded ? "No transfer yet. Close this dialog and scan the booking QR." : "Transfer details unavailable. Retry Details after checking the API connection."}</p>}
      {transfer && <>
        <div className="flex items-center gap-3"><StatusBadge status={transfer.status} /><span>{transfer.transferredEnergyKWh}/{transfer.expectedEnergyKWh} kWh</span></div>
        {transfer.completedAt && <p>Completed: {new Date(transfer.completedAt).toLocaleString()}</p>}
        <p>Prosumer NIC: {transfer.prosumerNIC || selected.prosumerNIC}</p>
        <p>{transfer.verifiedAt ? `Verified by ${transfer.verifiedBy}` : "Awaiting QR verification"}</p>
        {transfer.status === "Pending" && <Button variant="outline" disabled={busy || !transfer.verifiedAt} onClick={() => void change("start")}>Start transfer</Button>}
        {transfer.status === "InProgress" && <div className="flex flex-wrap gap-2">
          <Input aria-label="Delivered kWh" type="number" min="0" max={transfer.expectedEnergyKWh} step="0.001" className={field} value={energy} onChange={e => setEnergy(e.target.value)} />
          <Button variant="outline" disabled={busy || !energy.trim()} onClick={() => void change("progress")}>Save reading</Button>
          <Button variant="outline" disabled={busy || !energy.trim()} onClick={() => void change("complete")}>Complete</Button>
        </div>}
        {["Pending", "InProgress"].includes(transfer.status) && <div className="flex gap-2">
          <Input aria-label="Cancellation or failure reason" className={field} placeholder="Reason" maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
          <Button variant="outline" disabled={busy || !reason.trim()} onClick={() => void change(transfer.status === "Pending" ? "cancel" : "fail")}>{transfer.status === "Pending" ? "Cancel" : "Fail"}</Button>
        </div>}
        <h3 className="font-medium">Transfer history</h3>
        {transfer.history.map((event, i) => <p key={i} className="text-sm">{new Date(event.at).toLocaleString()} · {event.action} · {event.status} · {event.actorNIC}</p>)}
      </>}
    </div>}
    </DialogContent></Dialog>
  </main>
}
