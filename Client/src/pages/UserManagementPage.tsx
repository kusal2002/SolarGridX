import { useEffect, useState } from "react"
import {
  Check,
  Mail,
  Pencil,
  Plus,
  Search,
  UserRound,
  UserRoundCog,
  X,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  createStaffUser,
  getMyProfile,
  getUsers,
  requestDeactivation,
  updateProfile,
  updateUserStatus,
} from "@/lib/auth-api"
import type { User } from "@/types/user"

function StatusBadge({ status }: { status: string }) {
  const style =
    status === "Active"
      ? "bg-emerald-100 text-emerald-700"
      : status === "Inactive"
        ? "bg-red-100 text-red-700"
        : "bg-amber-100 text-amber-700"
  return (
    <span className={`rounded-full px-2.5 py-1 text-xs font-medium ${style}`}>
      {status}
    </span>
  )
}

function StatusActions({
  user,
  busy,
  onChange,
}: {
  user: User
  busy: boolean
  onChange: (status: string) => void
}) {
  if (user.accountStatus === "Pending") {
    return (
      <Button
        variant="outline"
        size="sm"
        disabled={busy}
        onClick={() => onChange("Active")}
      >
        Approve account
      </Button>
    )
  }
  if (user.accountStatus === "Active") {
    return (
      <Button
        variant="ghost"
        size="sm"
        disabled={busy}
        onClick={() => onChange("Inactive")}
      >
        Deactivate
      </Button>
    )
  }
  if (user.accountStatus === "Inactive") {
    return (
      <Button
        variant="outline"
        size="sm"
        disabled={busy}
        onClick={() => onChange("Active")}
      >
        Reactivate
      </Button>
    )
  }
  if (user.accountStatus === "DeactivationRequested") {
    return (
      <div className="flex justify-end gap-2">
        <Button
          variant="destructive"
          size="sm"
          disabled={busy}
          onClick={() => onChange("Inactive")}
        >
          Approve deactivation
        </Button>
        <Button
          variant="outline"
          size="sm"
          disabled={busy}
          onClick={() => onChange("Active")}
        >
          Keep active
        </Button>
      </div>
    )
  }
  return null
}

export function UserManagementPage({
  currentUser,
  onUserUpdated,
}: {
  currentUser: User
  onUserUpdated: (user: User) => void
}) {
  const isBackoffice = currentUser.role === "Backoffice"
  const [users, setUsers] = useState<User[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [profile, setProfile] = useState(currentUser)
  const [search, setSearch] = useState("")
  const [status, setStatus] = useState("All")
  const [role, setRole] = useState("All")
  const [sortBy, setSortBy] = useState("createdAt")
  const [sortDirection, setSortDirection] = useState("desc")
  const [page, setPage] = useState(1)
  const pageSize = 10
  const [editing, setEditing] = useState(false)
  const [showStaff, setShowStaff] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [success, setSuccess] = useState("")
  const [profileForm, setProfileForm] = useState({
    name: currentUser.name,
    email: currentUser.email,
  })
  const [staffForm, setStaffForm] = useState({
    nic: "",
    name: "",
    email: "",
    password: "",
    role: "Grid Operator" as "Backoffice" | "Grid Operator",
  })

  useEffect(() => {
    if (!isBackoffice) return
    const loadUsers = async () => {
      setBusy(true)
      setError("")
      try {
        const result = await getUsers(
          status,
          role,
          search,
          page,
          pageSize,
          sortBy,
          sortDirection
        )
        setUsers(result.items)
        setTotalCount(result.totalCount)
        setTotalPages(result.totalPages)
      } catch (loadError) {
        setError(
          loadError instanceof Error
            ? loadError.message
            : "Unable to load users."
        )
      } finally {
        setBusy(false)
      }
    }
    void loadUsers()
  }, [isBackoffice, status, role, search, page, sortBy, sortDirection])
  useEffect(() => {
    void getMyProfile()
      .then(setProfile)
      .catch(() => undefined)
  }, [])

  const saveProfile = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const updated = await updateProfile(
        profile.nic,
        profileForm.name,
        profileForm.email
      )
      setProfile(updated)
      onUserUpdated(updated)
      setEditing(false)
      setSuccess("Profile updated successfully.")
    } catch (saveError) {
      setError(
        saveError instanceof Error
          ? saveError.message
          : "Unable to update profile."
      )
    } finally {
      setBusy(false)
    }
  }
  const changeStatus = async (user: User, nextStatus: string) => {
    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const updated = await updateUserStatus(user.nic, nextStatus)
      setUsers((items) =>
        items.map((item) => (item.nic === updated.nic ? updated : item))
      )
      setSuccess(`${user.name} is now ${updated.accountStatus}.`)
    } catch (statusError) {
      setError(
        statusError instanceof Error
          ? statusError.message
          : "Unable to update account status."
      )
    } finally {
      setBusy(false)
    }
  }
  const deactivate = async () => {
    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const updated = await requestDeactivation(profile.nic)
      setProfile(updated)
      onUserUpdated(updated)
      setSuccess("Your deactivation request was submitted.")
    } catch (deactivationError) {
      setError(
        deactivationError instanceof Error
          ? deactivationError.message
          : "Unable to request deactivation."
      )
    } finally {
      setBusy(false)
    }
  }
  const addStaff = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const created = await createStaffUser(staffForm)
      setUsers((items) => [created, ...items])
      setTotalCount((count) => count + 1)
      setShowStaff(false)
      setStaffForm({
        nic: "",
        name: "",
        email: "",
        password: "",
        role: "Grid Operator",
      })
      setSuccess(`${created.name} was added as ${created.role}.`)
    } catch (staffError) {
      setError(
        staffError instanceof Error
          ? staffError.message
          : "Unable to create staff account."
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-6">
      <div>
        <p className="text-sm font-medium text-emerald-600">
          Member 1 · Identity
        </p>
        <h1 className="text-2xl font-semibold tracking-tight">
          {isBackoffice ? "User management" : "My account"}
        </h1>
        <p className="text-muted-foreground">
          Manage secure access across the SolarGridX network.
        </p>
      </div>
      {error && (
        <p className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-600">
          {error}
        </p>
      )}
      {success && (
        <p className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4 text-sm text-emerald-700">
          {success}
        </p>
      )}
      <section className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(320px,0.7fr)]">
        <div className="rounded-xl border bg-card p-5">
          <div className="mb-5 flex items-start justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Signed in as</p>
              <h2 className="mt-1 text-xl font-semibold">{profile.name}</h2>
              <p className="text-sm text-muted-foreground">
                {profile.role} · {profile.nic}
              </p>
            </div>
            <div className="flex size-11 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
              <UserRound />
            </div>
          </div>
          {editing ? (
            <form onSubmit={saveProfile} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="profile-name">Full name</Label>
                <Input
                  id="profile-name"
                  value={profileForm.name}
                  onChange={(e) =>
                    setProfileForm({ ...profileForm, name: e.target.value })
                  }
                  required
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="profile-email">Email</Label>
                <Input
                  id="profile-email"
                  type="email"
                  value={profileForm.email}
                  onChange={(e) =>
                    setProfileForm({ ...profileForm, email: e.target.value })
                  }
                  required
                />
              </div>
              <div className="flex gap-2">
                <Button disabled={busy}>
                  <Check />
                  Save changes
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => setEditing(false)}
                >
                  <X />
                  Cancel
                </Button>
              </div>
            </form>
          ) : (
            <div className="space-y-3 text-sm">
              <div className="flex items-center gap-2 text-muted-foreground">
                <Mail className="size-4" />
                {profile.email}
              </div>
              <StatusBadge status={profile.accountStatus} />
              <div className="pt-2">
                <Button variant="outline" onClick={() => setEditing(true)}>
                  <Pencil />
                  Edit profile
                </Button>
              </div>
            </div>
          )}
        </div>
        {currentUser.role === "Prosumer" ? (
          <div className="rounded-xl border bg-slate-950 p-5 text-white">
            <p className="text-sm text-emerald-300">Account controls</p>
            <h2 className="mt-2 text-xl font-semibold">
              Keep your access current
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-300">
              Request deactivation when you no longer need access. A backoffice
              operator will review the request.
            </p>
            <Button
              className="mt-5 bg-white text-slate-950 hover:bg-slate-100"
              disabled={profile.accountStatus !== "Active" || busy}
              onClick={deactivate}
            >
              Request deactivation
            </Button>
          </div>
        ) : (
          <div className="rounded-xl border bg-emerald-50 p-5">
            <UserRoundCog className="size-7 text-emerald-700" />
            <p className="mt-4 text-sm font-medium text-emerald-900">
              {isBackoffice ? "Backoffice access" : "Operator access"}
            </p>
            <p className="mt-1 text-sm leading-6 text-emerald-800">
              {isBackoffice
                ? "You can activate, deactivate, and provision staff accounts from the directory below."
                : "You can access station operations and manage your own profile."}
            </p>
          </div>
        )}
      </section>
      {isBackoffice && (
        <section className="rounded-xl border bg-card">
          <div className="flex flex-col gap-4 border-b p-5 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <h2 className="font-semibold">Account directory</h2>
              <p className="text-sm text-muted-foreground">
                {totalCount} registered account{totalCount === 1 ? "" : "s"}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <div className="flex items-center gap-2 rounded-lg border px-3 py-2 sm:w-64">
                <Search className="size-4 text-muted-foreground" />
                <input
                  className="w-full bg-transparent text-sm outline-none"
                  placeholder="Search users..."
                  value={search}
                  onChange={(e) => {
                    setSearch(e.target.value)
                    setPage(1)
                  }}
                />
              </div>
              <select
                className="rounded-lg border bg-background px-3 py-2 text-sm outline-none"
                value={status}
                onChange={(e) => {
                  setStatus(e.target.value)
                  setPage(1)
                }}
              >
                <option>All</option>
                <option>Active</option>
                <option>Inactive</option>
                <option>Pending</option>
                <option>DeactivationRequested</option>
              </select>
              <select
                className="rounded-lg border bg-background px-3 py-2 text-sm outline-none"
                value={role}
                onChange={(e) => {
                  setRole(e.target.value)
                  setPage(1)
                }}
              >
                <option>All</option>
                <option>Prosumer</option>
                <option>Backoffice</option>
                <option>Grid Operator</option>
              </select>
              <select
                className="rounded-lg border bg-background px-3 py-2 text-sm outline-none"
                value={`${sortBy}:${sortDirection}`}
                onChange={(e) => {
                  const [nextSortBy, nextSortDirection] = e.target.value.split(":")
                  setSortBy(nextSortBy)
                  setSortDirection(nextSortDirection)
                  setPage(1)
                }}
              >
                <option value="createdAt:desc">Newest first</option>
                <option value="createdAt:asc">Oldest first</option>
                <option value="name:asc">Name A-Z</option>
                <option value="name:desc">Name Z-A</option>
                <option value="role:asc">Role A-Z</option>
              </select>
              <Button onClick={() => setShowStaff(!showStaff)}>
                <Plus />
                Add staff
              </Button>
            </div>
          </div>
          {showStaff && (
            <form
              onSubmit={addStaff}
              className="grid gap-3 border-b bg-muted/20 p-5 sm:grid-cols-2 lg:grid-cols-5"
            >
              <Input
                placeholder="NIC"
                required
                value={staffForm.nic}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, nic: e.target.value })
                }
              />
              <Input
                placeholder="Full name"
                required
                value={staffForm.name}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, name: e.target.value })
                }
              />
              <Input
                type="email"
                placeholder="Email"
                required
                value={staffForm.email}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, email: e.target.value })
                }
              />
              <Input
                type="password"
                minLength={8}
                placeholder="Password"
                required
                value={staffForm.password}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, password: e.target.value })
                }
              />
              <div className="flex gap-2">
                <select
                  className="min-w-0 flex-1 rounded-lg border bg-background px-3 text-sm"
                  value={staffForm.role}
                  onChange={(e) =>
                    setStaffForm({
                      ...staffForm,
                      role: e.target.value as "Backoffice" | "Grid Operator",
                    })
                  }
                >
                  <option>Grid Operator</option>
                  <option>Backoffice</option>
                </select>
                <Button type="submit" disabled={busy}>
                  Create
                </Button>
              </div>
            </form>
          )}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-muted/50">
                <tr className="border-b">
                  <th className="px-5 py-3 font-medium">User</th>
                  <th className="px-5 py-3 font-medium">NIC</th>
                  <th className="px-5 py-3 font-medium">Role</th>
                  <th className="px-5 py-3 font-medium">Status</th>
                  <th className="px-5 py-3 text-right font-medium">Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.nic} className="border-b last:border-0">
                    <td className="px-5 py-3">
                      <p className="font-medium">{user.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {user.email}
                      </p>
                    </td>
                    <td className="px-5 py-3">{user.nic}</td>
                    <td className="px-5 py-3">{user.role}</td>
                    <td className="px-5 py-3">
                      <StatusBadge status={user.accountStatus} />
                    </td>
                    <td className="px-5 py-3 text-right">
                      {user.nic !== profile.nic && (
                        <StatusActions
                          user={user}
                          busy={busy}
                          onChange={(nextStatus) =>
                            changeStatus(user, nextStatus)
                          }
                        />
                      )}
                    </td>
                  </tr>
                ))}
                {!busy && users.length === 0 && (
                  <tr>
                    <td
                      colSpan={5}
                      className="px-5 py-8 text-center text-muted-foreground"
                    >
                      No users match these filters.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <div className="flex flex-col gap-3 border-t p-4 text-sm text-muted-foreground sm:flex-row sm:items-center sm:justify-between">
            <span>
              {totalCount === 0
                ? "No users"
                : `Showing ${(page - 1) * pageSize + 1}-${Math.min(page * pageSize, totalCount)} of ${totalCount}`}
            </span>
            {totalPages > 1 && (
              <div className="flex items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 1 || busy}
                  onClick={() => setPage((currentPage) => currentPage - 1)}
                >
                  Previous
                </Button>
                <span>
                  Page {page} of {totalPages}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === totalPages || busy}
                  onClick={() => setPage((currentPage) => currentPage + 1)}
                >
                  Next
                </Button>
              </div>
            )}
          </div>
        </section>
      )}
    </div>
  )
}
