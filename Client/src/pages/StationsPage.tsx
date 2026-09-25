import { useEffect, useState } from "react"
import { getStations } from "@/lib/station-api"
import type { Station } from "@/types/station"
import { Button } from "@/components/ui/button"
import { Plus, Search, Eye, Pencil, Power } from "lucide-react"

export function StationsPage() {
  const [stations, setStations] = useState<Station[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState("")
  const [searchTerm, setSearchTerm] = useState("")

  const [statusFilter, setStatusFilter] = useState<
    "All" | "Active" | "Inactive"
  >("All")

  useEffect(() => {
    async function loadStations() {
      try {
        setLoading(true)
        setError("")

        const data = await getStations()
        setStations(data)
      } catch (error) {
        console.error(error)
        setError("Unable to load stations.")
      } finally {
        setLoading(false)
      }
    }

    loadStations()
  }, [])

  const filteredStations = stations.filter((station) => {
    const search = searchTerm.toLowerCase()

    const matchesSearch =
      station.stationName.toLowerCase().includes(search) ||
      station.location.toLowerCase().includes(search)

    const matchesStatus =
      statusFilter === "All" ||
      (statusFilter === "Active" && station.isActive) ||
      (statusFilter === "Inactive" && !station.isActive)

    return matchesSearch && matchesStatus
  })

  const totalStations = stations.length
  const activeStations = stations.filter((station) => station.isActive).length

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">
            Station Management
          </h1>

          <p className="text-muted-foreground">
            Manage solar stations and their energy capacity.
          </p>
        </div>

        <Button>
          <Plus className="mr-2 size-4" />
          Add Station
        </Button>
      </div>

      {/* Summary Cards */}
      <div className="grid gap-4 sm:grid-cols-3">
        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Total Stations</p>
          <p className="mt-2 text-2xl font-semibold">{totalStations}</p>
        </div>

        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Active Stations</p>
          <p className="mt-2 text-2xl font-semibold text-green-600">
            {activeStations}
          </p>
        </div>

        <div className="rounded-xl border bg-card p-4">
          <p className="text-sm text-muted-foreground">Inactive Stations</p>
          <p className="mt-2 text-2xl font-semibold text-red-600">
            {totalStations - activeStations}
          </p>
        </div>
      </div>

      {/* Search and Filter */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
        {/* Search */}
        <div className="flex items-center gap-2 rounded-lg border px-3 py-2 sm:w-80">
          <Search className="size-4 text-muted-foreground" />

          <input
            type="text"
            placeholder="Search stations..."
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            className="w-full bg-transparent text-sm outline-none"
          />
        </div>

        {/* Status Filter */}
        <select
          value={statusFilter}
          onChange={(event) =>
            setStatusFilter(event.target.value as "All" | "Active" | "Inactive")
          }
          className="rounded-lg border bg-background px-3 py-2 text-sm outline-none"
        >
          <option value="All">All Stations</option>
          <option value="Active">Active Stations</option>
          <option value="Inactive">Inactive Stations</option>
        </select>
      </div>

      {/* Loading State */}
      {loading && (
        <p className="text-sm text-muted-foreground">Loading stations...</p>
      )}

      {/* Error State */}
      {!loading && error && (
        <p className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-500">
          {error}
        </p>
      )}

      {/* Station Table */}
      {!loading && !error && (
        <div className="overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted/50">
              <tr className="border-b">
                <th className="px-4 py-3 font-medium">Station ID</th>
                <th className="px-4 py-3 font-medium">Station Name</th>
                <th className="px-4 py-3 font-medium">Location</th>
                <th className="px-4 py-3 font-medium">Capacity (kWh)</th>
                <th className="px-4 py-3 font-medium">Status</th>
                <th className="px-4 py-3 text-right font-medium">Actions</th>
              </tr>
            </thead>

            <tbody>
              {filteredStations.map((station) => (
                <tr
                  key={station.id}
                  className="border-b last:border-0 hover:bg-muted/30"
                >
                  <td className="px-4 py-3 font-medium">{station.id}</td>

                  <td className="px-4 py-3">{station.stationName}</td>

                  <td className="px-4 py-3">{station.location}</td>

                  <td className="px-4 py-3">{station.totalCapacityKwh}</td>

                  <td className="px-4 py-3">
                    <span
                      className={
                        station.isActive
                          ? "rounded-full bg-green-100 px-2 py-1 text-xs font-medium text-green-700"
                          : "rounded-full bg-red-100 px-2 py-1 text-xs font-medium text-red-700"
                      }
                    >
                      {station.isActive ? "Active" : "Inactive"}
                    </span>
                  </td>

                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <Button variant="ghost" size="icon">
                        <Eye className="size-4" />
                      </Button>

                      <Button variant="ghost" size="icon">
                        <Pencil className="size-4" />
                      </Button>

                      <Button variant="ghost" size="icon">
                        <Power className="size-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}

              {filteredStations.length === 0 && (
                <tr>
                  <td
                    colSpan={6}
                    className="px-4 py-8 text-center text-muted-foreground"
                  >
                    No stations found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
