import { Button } from "@/components/ui/button"
import { Building2, ShieldCheck, BatteryCharging, Zap, Clock3, CalendarCheck2, Activity, CheckCircle2, Gauge, RefreshCw } from "lucide-react"
import { useCallback, useEffect, useState } from "react"
import { request } from "@/lib/api-client"
import { useAuth } from "@/context/AuthContext"
import type { Station } from "@/types/station"

type Stats = { pending: number; current: number; completedTransfers: number; deliveredEnergyKwh: number; stationCount: number; activeStations: number; activeSlots: number; availableEnergyKwh: number; activeTransfers: number }
type Completed = { id: string; stationId: string; prosumerNIC: string; transferredEnergyKWh: number; completedAt?: string }

export function DashboardPage() {
  const { user } = useAuth()
  const [stations, setStations] = useState<Station[]>([])
  const [stationId, setStationId] = useState("")
  const [stats, setStats] = useState<Stats | null>(null)
  const [completed, setCompleted] = useState<Completed[]>([])
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)
  const load = useCallback(async () => {
    setLoading(true); setError("")
    try {
      const suffix = stationId ? `&stationId=${encodeURIComponent(stationId)}` : ""
      const [list, summary, history] = await Promise.all([
        request<Station[]>("/stations/all"),
        request<Stats>(`/dashboard/summary?${suffix.slice(1)}`),
        request<Completed[]>(`/energy-transfers?status=Completed&pageSize=5${suffix}`),
      ])
      setStations(list); setStats(summary); setCompleted(history)
    } catch (e) { setError(e instanceof Error ? e.message : "Could not load dashboard"); setStats(null); setCompleted([]) }
    finally { setLoading(false) }
  }, [stationId])
  useEffect(() => {
    const timer = window.setTimeout(() => { void load() }, 0)
    return () => window.clearTimeout(timer)
  }, [load])
  const icons = [Building2, ShieldCheck, BatteryCharging, Zap, Clock3, CalendarCheck2, Activity, CheckCircle2, Gauge]
  const cards = stats ? [
    ["Stations", stats.stationCount], ["Active stations", stats.activeStations],
    ["Upcoming active slots", stats.activeSlots], ["Available energy (kWh)", stats.availableEnergyKwh],
    ["Pending bookings", stats.pending], ["Current bookings", stats.current],
    ["Transfers in progress", stats.activeTransfers], ["Completed transfers", stats.completedTransfers],
    ["Completed energy (kWh)", stats.deliveredEnergyKwh],
  ] : []
  return <main className="space-y-6 p-6">
    <h1 className="text-2xl font-semibold">{user?.role === "Grid Operator" ? "My station dashboard" : "System dashboard"}</h1>
    <p className="text-muted-foreground">{user?.role === "Grid Operator" ? "Statistics for your assigned stations." : "Station operations and energy delivery across the system."}</p>
    <div className="flex gap-3">
      <select aria-label="Dashboard station" className="rounded-md border bg-background p-2" value={stationId} onChange={e => setStationId(e.target.value)}>
        <option value="">All accessible stations</option>
        {stations.map(s => <option key={s.id} value={s.id}>{s.stationName}</option>)}
      </select>
      <Button variant="outline" disabled={loading} onClick={() => void load()}><RefreshCw />{loading ? "Loading…" : "Refresh"}</Button>
    </div>
    {error && <p role="alert">{error}</p>}
    {!loading && !error && user?.role === "Grid Operator" && !stations.length && <p>Ask Backoffice to assign your account to a station in Station Management.</p>}
    <div className="grid gap-3 sm:grid-cols-3">{cards.map(([label, value], index) => <div key={label} className="rounded-xl border bg-card p-4">
      <div className="mb-3 flex items-center justify-between gap-2"><p className="text-sm text-muted-foreground">{label}</p><span className="rounded-lg bg-muted p-2 text-muted-foreground">{(() => { const Icon = icons[index]; return <Icon className="size-4" /> })()}</span></div><p className="text-2xl font-semibold">{typeof value === "number" ? Number(value.toFixed(3)) : value}</p>
    </div>)}</div>
    <h2 className="text-lg font-semibold">Recently completed transfers</h2>
    {!completed.length && <p>No completed transfers yet.</p>}
    {completed.map(t => <div key={t.id} className="rounded-xl border p-4">
      <p className="font-semibold">{stations.find(s => s.id === t.stationId)?.stationName ?? "Station unavailable"}</p>
      <p>{t.prosumerNIC} · {t.transferredEnergyKWh} kWh · {t.completedAt ? new Date(t.completedAt).toLocaleString() : "Completed"}</p>
    </div>)}
  </main>
}
