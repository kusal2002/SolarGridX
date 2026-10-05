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
const button = "rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground disabled:opacity-50"

export function TransfersPage() {
  const [summary, setSummary] = useState<Summary | null>(null)
  const [view, setView] = useState("current")
  const [search, setSearch] = useState("")
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const [bookings, setBookings] = useState<EnergyReservation[]>([])
  const [selected, setSelected] = useState<EnergyReservation | null>(null)
  const [transfer, setTransfer] = useState<Transfer | null>(null)
  const [scanning, setScanning] = useState(false)
  const [transferLoaded, setTransferLoaded] = useState(false)
  const [energy, setEnergy] = useState("")
  const [reason, setReason] = useState("")
  const [message, setMessage] = useState("")
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    const [counts, result] = await Promise.all([
      request<Summary>("/dashboard/summary"),
      request<{ items: EnergyReservation[]; total: number }>(`/bookings/${view}?page=${page}&pageSize=20&search=${encodeURIComponent(search)}`),
    ])
    setSummary(counts); setBookings(result.items); setTotal(result.total)
  }, [view, page, search])

  useEffect(() => { let active = true
    const timer = window.setTimeout(() => { void load().catch(e => { if (active) setMessage(e.message) }) }, 250)
    return () => { active = false; window.clearTimeout(timer) }
  }, [load])

  async function run(operation: () => Promise<void>, refresh = true) {
    setBusy(true); setMessage("")
    try { await operation(); if (refresh) await load() }
    catch (e) { setMessage(e instanceof Error && e.message === "Failed to fetch"
      ? "Cannot reach the API. Check the backend is running and its URL/CORS settings, then refresh."
      : e instanceof Error ? e.message : "Request failed") }
    finally { setBusy(false) }
  }

  async function open(booking: EnergyReservation) {
    setSelected(booking); setTransfer(null); setTransferLoaded(false)
    await run(async () => {
      const list = await request<Transfer[]>(`/energy-transfers?reservationId=${booking.id}`)
      setTransfer(list[0] ?? null)
      setTransferLoaded(true)
      setEnergy(String(list[0]?.transferredEnergyKWh ?? 0))
    })
  }

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
        <p className="text-sm text-muted-foreground">{key === "approvedFuture" ? "Approved future" : key}</p>
        <p className="text-2xl font-semibold">{count}</p>
      </div>)}
    </div>
    <div className="flex flex-wrap gap-2">
      <select aria-label="Booking view" className={field} value={view} onChange={e => { setView(e.target.value); setPage(1) }}>
        {[["current", "Current"], ["pending", "Pending"], ["completed", "Completed transfers"], ["history", "Completed & cancelled history"], ["search", "All bookings"]].map(([value, label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      <input aria-label="Search bookings" className={field} placeholder="Reservation ID, NIC or station ID" value={search} onChange={e => { setSearch(e.target.value); setPage(1) }} />
      <button className={button} disabled={busy} onClick={() => void run(load)}>Refresh</button>
    </div>
    <p role="status" className="text-sm">{message}</p>
    <div className="overflow-x-auto rounded-lg border">
      <table className="w-full text-left text-sm"><thead><tr className="border-b bg-muted">
        {["Reservation", "Prosumer", "Date", "kWh", "Booking status", ""].map((title, i) => <th className="p-3" key={i}>{title}</th>)}
      </tr></thead><tbody>{bookings.map(b => <tr key={b.id} className="border-b">
        <td className="p-3">{b.id}</td><td className="p-3">{b.prosumerNIC}</td>
        <td className="p-3">{new Date(b.reservationDate).toLocaleDateString()} {b.startTime}</td>
        <td className="p-3">{b.requestedEnergyKwh}</td><td className="p-3">{b.status}</td>
        <td className="p-3"><button className={button} disabled={busy} onClick={() => void open(b)}>Details</button></td>
      </tr>)}</tbody></table>
      {!bookings.length && <p className="p-4">No bookings found.</p>}
    </div>
    <div className="flex items-center gap-3">
      <button className={button} disabled={page === 1 || busy} onClick={() => setPage(page - 1)}>Previous</button>
      <span>Page {page} · {total} bookings</span>
      <button className={button} disabled={page * 20 >= total || busy} onClick={() => setPage(page + 1)}>Next</button>
    </div>
    <section className="space-y-3 rounded-lg border p-4">
      <h2 className="text-lg font-semibold">Verify a transaction</h2>
      <p className="text-sm text-muted-foreground">Scan the prosumer’s approved booking QR. The server identifies the prosumer, station, slot, and reserved energy automatically.</p>
      <button className={button} disabled={busy || scanning} onClick={() => setScanning(true)}>Scan QR</button>
      {scanning && <QrScanner onScan={verify} onClose={() => setScanning(false)} />}
    </section>
    {selected && <section className="space-y-3 rounded-lg border p-4">
      <h2 className="text-lg font-semibold">Booking details</h2>
      <p>{selected.id} · {selected.prosumerNIC} · {selected.status}</p>
      <p>Station: {selected.stationId} · Slot: {selected.slotId}</p>
      <p>{selected.startTime}–{selected.endTime} · {selected.requestedEnergyKwh} kWh</p>
      {!transfer && <p>{transferLoaded ? "No transfer yet. Scan the reservation QR above." : "Transfer details unavailable. Retry Details after checking the API connection."}</p>}
      {transfer && <>
        <p>Transfer: {transfer.status} · {transfer.transferredEnergyKWh}/{transfer.expectedEnergyKWh} kWh</p>
        {transfer.completedAt && <p>Completed: {new Date(transfer.completedAt).toLocaleString()}</p>}
        <p>Prosumer NIC: {transfer.prosumerNIC || selected.prosumerNIC}</p>
        <p>{transfer.verifiedAt ? `Verified by ${transfer.verifiedBy}` : "Awaiting QR verification"}</p>
        {transfer.status === "Pending" && <button className={button} disabled={busy || !transfer.verifiedAt} onClick={() => void change("start")}>Start transfer</button>}
        {transfer.status === "InProgress" && <div className="flex flex-wrap gap-2">
          <input aria-label="Delivered kWh" type="number" min="0" max={transfer.expectedEnergyKWh} step="0.001" className={field} value={energy} onChange={e => setEnergy(e.target.value)} />
          <button className={button} disabled={busy || !energy.trim()} onClick={() => void change("progress")}>Save reading</button>
          <button className={button} disabled={busy || !energy.trim()} onClick={() => void change("complete")}>Complete</button>
        </div>}
        {["Pending", "InProgress"].includes(transfer.status) && <div className="flex gap-2">
          <input aria-label="Cancellation or failure reason" className={field} placeholder="Reason" maxLength={500} value={reason} onChange={e => setReason(e.target.value)} />
          <button className={button} disabled={busy || !reason.trim()} onClick={() => void change(transfer.status === "Pending" ? "cancel" : "fail")}>{transfer.status === "Pending" ? "Cancel" : "Fail"}</button>
        </div>}
        <h3 className="font-medium">Transfer history</h3>
        {transfer.history.map((event, i) => <p key={i} className="text-sm">{new Date(event.at).toLocaleString()} · {event.action} · {event.status} · {event.actorNIC}</p>)}
      </>}
    </section>}
  </main>
}
