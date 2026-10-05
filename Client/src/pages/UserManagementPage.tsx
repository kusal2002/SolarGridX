import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from "@/components/ui/table"
import { StatusBadge } from "@/components/status-badge"
import { useEffect, useState } from "react"
import {
  ArrowLeft,
  Ban,
  Check,
  CircleCheck,
  CircleOff,
  Clock3,
  Download,
  Mail,
  Pencil,
  Plus,
  Search,
  ShieldCheck,
  UserRound,
  UserRoundCog,
  Users,
  X,
} from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import {
  createStaffUser,
  getMyProfile,
  getUserByNic,
  getUsers,
  requestDeactivation as requestDeactivationApi,
  updateProfile,
  updateUserStatus,
} from "@/lib/auth-api"
import type { User } from "@/types/user"

function csvValue(value: string | number | null | undefined) {
  const text = value == null ? "" : String(value)
  return `"${text.replaceAll('"', '""')}"`
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
        variant="outline"
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
      <div className="flex items-center justify-end gap-2">
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
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [detailsBusy, setDetailsBusy] = useState(false)
  const [showStaff, setShowStaff] = useState(false)
  const [busy, setBusy] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [confirmation, setConfirmation] = useState<{
    message: string
    action: () => Promise<void>
  } | null>(null)
  const [error, setError] = useState("")
  const [success, setSuccess] = useState("")
  const [profileForm, setProfileForm] = useState({
    name: currentUser.name,
    email: currentUser.email,
  })
  const [userEditForm, setUserEditForm] = useState({ name: "", email: "" })

  const downloadUsers = async (
    filename: string,
    filterRole?: string,
    filterStatus?: string
  ) => {
    setDownloading(true)
    setError("")
    try {
      const downloadedUsers: User[] = []
      let currentPage = 1
      let totalPages = 1

      do {
        const result = await getUsers(
          filterStatus,
          filterRole,
          "",
          currentPage,
          100,
          "createdAt",
          "desc"
        )
        downloadedUsers.push(...result.items)
        totalPages = result.totalPages
        currentPage += 1
      } while (currentPage <= totalPages)

      const rows = [
        ["NIC", "Name", "Email", "Role", "Status", "Created At"],
        ...downloadedUsers.map((user) => [
          user.nic,
          user.name,
          user.email,
          user.role,
          user.accountStatus,
          user.createdAt,
        ]),
      ]
      const csv = rows.map((row) => row.map(csvValue).join(",")).join("\n")
      const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }))
      const link = document.createElement("a")
      link.href = url
      link.download = `${filename}.csv`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    } catch (downloadError) {
      setError(
        downloadError instanceof Error
          ? downloadError.message
          : "Unable to download users."
      )
    } finally {
      setDownloading(false)
    }
  }
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
  const startEditingUser = (user: User) => {
    setEditingUser(user)
    setUserEditForm({ name: user.name, email: user.email })
    setError("")
    setSuccess("")
  }
  const openUserDetails = async (user: User) => {
    setDetailsBusy(true)
    setError("")
    try {
      setSelectedUser(await getUserByNic(user.nic))
    } catch (detailsError) {
      setError(
        detailsError instanceof Error
          ? detailsError.message
          : "Unable to load user details."
      )
    } finally {
      setDetailsBusy(false)
    }
  }
  const saveUser = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!editingUser) return

    setBusy(true)
    setError("")
    setSuccess("")
    try {
      const updated = await updateProfile(
        editingUser.nic,
        userEditForm.name,
        userEditForm.email
      )
      setUsers((items) =>
        items.map((item) => (item.nic === updated.nic ? updated : item))
      )
      setEditingUser(null)
      setSuccess(`${updated.name}'s account was updated.`)
    } catch (saveError) {
      setError(
        saveError instanceof Error
          ? saveError.message
          : "Unable to update user account."
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
  const requestStatusChange = (user: User, nextStatus: string) => {
    setConfirmation({
      message: `Are you sure you want to change ${user.name}'s account status to ${nextStatus}?`,
      action: () => changeStatus(user, nextStatus),
    })
  }
  const askDeactivation = () => {
    setConfirmation({
      message: "Are you sure you want to request deactivation of your account?",
      action: async () => {
        setBusy(true)
        setError("")
        setSuccess("")
        try {
          const updated = await requestDeactivationApi(profile.nic)
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
      },
    })
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
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">{isBackoffice ? "User management" : "My account"}</h1>
          <p className="text-sm text-muted-foreground">{isBackoffice ? "Manage users, account access, and staff roles." : "Manage your profile and account access."}</p>
        </div>
        {isBackoffice && <Button onClick={() => { setError(""); setShowStaff(true) }}><Plus /> Add user</Button>}
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
      {!isBackoffice && (      <section className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(320px,0.7fr)]">
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
              onClick={askDeactivation}
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
      )}
      {isBackoffice && selectedUser && (
        <Dialog open onOpenChange={(open) => !open && setSelectedUser(null)}><DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-2xl"><DialogHeader><DialogTitle>User details</DialogTitle><DialogDescription>Account information and available actions.</DialogDescription></DialogHeader><div>
          <div className="flex items-start justify-between gap-4 border-b pb-5">
            <div>
              <Button variant="outline" size="sm" onClick={() => setSelectedUser(null)}>
                <ArrowLeft />
                Back to directory
              </Button>
              <p className="mt-4 text-sm text-muted-foreground">User details</p>
              <h2 className="mt-1 text-2xl font-semibold">{selectedUser.name}</h2>
              <p className="text-sm text-muted-foreground">{selectedUser.email}</p>
            </div>
            {detailsBusy && <span className="text-sm text-muted-foreground">Loading...</span>}
          </div>
          <dl className="grid gap-4 py-5 sm:grid-cols-2">
            <div><dt className="text-sm text-muted-foreground">NIC</dt><dd className="mt-1 font-medium">{selectedUser.nic}</dd></div>
            <div><dt className="text-sm text-muted-foreground">Role</dt><dd className="mt-1 font-medium">{selectedUser.role}</dd></div>
            <div><dt className="text-sm text-muted-foreground">Status</dt><dd className="mt-1"><StatusBadge status={selectedUser.accountStatus} /></dd></div>
            <div><dt className="text-sm text-muted-foreground">Created</dt><dd className="mt-1 font-medium">{new Date(selectedUser.createdAt).toLocaleString()}</dd></div>
          </dl>
          <div className="flex flex-wrap gap-2 border-t pt-5">
            {selectedUser.nic !== profile.nic && (
              <>
                <Button
                  variant="outline"
                  onClick={() => {
                    setSelectedUser(null)
                    startEditingUser(selectedUser)
                  }}
                >
                  <Pencil />
                  Edit user
                </Button>
                <StatusActions
                  user={selectedUser}
                  busy={busy}
                  onChange={(nextStatus) =>
                    requestStatusChange(selectedUser, nextStatus)
                  }
                />
              </>
            )}
          </div>
        </div></DialogContent></Dialog>
      )}
      {isBackoffice && (
        <section className="rounded-xl border bg-card">
          <div className="space-y-4 border-b p-4">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <p className="text-sm text-muted-foreground">{totalCount} registered account{totalCount === 1 ? "" : "s"}</p>
              <div className="flex gap-2">
                {(search || status !== "All" || role !== "All" || sortBy !== "createdAt" || sortDirection !== "desc") && <Button variant="outline" size="sm" onClick={() => { setSearch(""); setStatus("All"); setRole("All"); setSortBy("createdAt"); setSortDirection("desc"); setPage(1) }}><X /> Clear filters</Button>}
              <DropdownMenu>
                <DropdownMenuTrigger
                  render={
                    <Button variant="outline" disabled={downloading}>
                      <Download />
                      {downloading ? "Preparing..." : "Download users"}
                    </Button>
                  }
                />
                <DropdownMenuContent align="end" className="w-64 p-2">
                  <DropdownMenuGroup>
                    <DropdownMenuLabel className="px-3 pb-2 pt-1 text-[11px] uppercase tracking-wider">
                      Download by role
                    </DropdownMenuLabel>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("all-users")}>
                      <Users />
                      All users
                    </DropdownMenuItem>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("prosumers", "Prosumer")}>
                      <UserRound />
                      Prosumers
                    </DropdownMenuItem>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("backoffice-users", "Backoffice")}>
                      <ShieldCheck />
                      Backoffice users
                    </DropdownMenuItem>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("grid-operators", "Grid Operator")}>
                      <UserRoundCog />
                      Grid Operators
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                  <DropdownMenuSeparator className="my-2" />
                  <DropdownMenuGroup>
                    <DropdownMenuLabel className="px-3 pb-2 pt-1 text-[11px] uppercase tracking-wider">
                      Download by status
                    </DropdownMenuLabel>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("pending-users", undefined, "Pending")}>
                      <Clock3 />
                      Pending users
                    </DropdownMenuItem>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("active-users", undefined, "Active")}>
                      <CircleCheck className="text-emerald-600" />
                      Active users
                    </DropdownMenuItem>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("inactive-users", undefined, "Inactive")}>
                      <CircleOff className="text-red-600" />
                      Inactive users
                    </DropdownMenuItem>
                    <DropdownMenuItem className="px-3 py-2" onClick={() => void downloadUsers("deactivation-requested-users", undefined, "DeactivationRequested")}>
                      <Ban className="text-amber-600" />
                      Deactivation requested
                    </DropdownMenuItem>
                  </DropdownMenuGroup>
                </DropdownMenuContent>
              </DropdownMenu>
              </div>
            </div>
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[2fr_1fr_1fr_1fr]">
              <div className="space-y-1.5">
                <Label htmlFor="user-search">Search users</Label>
                <div className="relative"><Search className="pointer-events-none absolute left-3 top-2.5 size-4 text-muted-foreground" /><Input id="user-search" className="pl-9" placeholder="Name, email or NIC" value={search} onChange={e => { setSearch(e.target.value); setPage(1) }} /></div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="user-status">Status</Label>
                <select id="user-status" className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm" value={status} onChange={e => { setStatus(e.target.value); setPage(1) }}>
                  <option value="All">All statuses</option><option>Active</option><option>Inactive</option><option>Pending</option><option value="DeactivationRequested">Deactivation requested</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="user-role">Role</Label>
                <select id="user-role" className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm" value={role} onChange={e => { setRole(e.target.value); setPage(1) }}>
                  <option value="All">All roles</option><option>Prosumer</option><option>Grid Operator</option><option>Backoffice</option>
                </select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="user-sort">Sort by</Label>
                <select id="user-sort" className="h-9 w-full rounded-md border border-input bg-background px-3 text-sm" value={sortBy + ":" + sortDirection} onChange={e => { const [key,direction] = e.target.value.split(":"); setSortBy(key); setSortDirection(direction); setPage(1) }}>
                  <option value="createdAt:desc">Newest first</option><option value="createdAt:asc">Oldest first</option><option value="name:asc">Name A–Z</option><option value="name:desc">Name Z–A</option><option value="role:asc">Role A–Z</option>
                </select>
              </div>
            </div>
          </div>
          <Dialog open={editingUser !== null} onOpenChange={open => !open && setEditingUser(null)}><DialogContent><DialogHeader><DialogTitle>Edit user</DialogTitle><DialogDescription>Update the user’s name and email.</DialogDescription></DialogHeader>
            <form
              onSubmit={saveUser}
              className="grid gap-4"
            >
              <div className="space-y-2">
                <Label htmlFor="edit-user-name">Full name</Label>
                <Input
                  id="edit-user-name"
                  required
                  value={userEditForm.name}
                  onChange={(e) =>
                    setUserEditForm({ ...userEditForm, name: e.target.value })
                  }
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="edit-user-email">Email</Label>
                <Input
                  id="edit-user-email"
                  type="email"
                  required
                  value={userEditForm.email}
                  onChange={(e) =>
                    setUserEditForm({ ...userEditForm, email: e.target.value })
                  }
                />
              </div>
              <Button type="submit" disabled={busy}>
                <Check />
                Save user
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => setEditingUser(null)}
              >
                <X />
                Cancel
              </Button>
            </form>
          </DialogContent></Dialog>
          <Dialog open={showStaff} onOpenChange={setShowStaff}><DialogContent className="sm:max-w-lg"><DialogHeader><DialogTitle>Add user</DialogTitle><DialogDescription>Create a Grid Operator or Backoffice account.</DialogDescription></DialogHeader>{error && <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-sm text-destructive">{error}</p>}
            <form
              onSubmit={addStaff}
              className="grid gap-4 sm:grid-cols-2"
            >
              <div className="space-y-2"><Label>NIC</Label><Input
                aria-label="NIC" placeholder="NIC"
                required
                value={staffForm.nic}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, nic: e.target.value })
                }
              /></div>
              <div className="space-y-2"><Label>Full name</Label><Input
                aria-label="Full name" placeholder="Full name"
                required
                value={staffForm.name}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, name: e.target.value })
                }
              /></div>
              <Input
                type="email"
                aria-label="Email" placeholder="Email"
                required
                value={staffForm.email}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, email: e.target.value })
                }
              />
              <Input
                type="password"
                minLength={8}
                aria-label="Password" placeholder="Password"
                required
                value={staffForm.password}
                onChange={(e) =>
                  setStaffForm({ ...staffForm, password: e.target.value })
                }
              />
              <div className="col-span-full flex gap-2">
                <select aria-label="User role"
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
          </DialogContent></Dialog>
          <div className="overflow-x-auto">
            <Table className="w-full text-left text-sm">
              <TableHeader className="bg-muted/40">
                <TableRow className="border-b">
                  <TableHead className="px-5 py-3 font-medium">User</TableHead>

                  <TableHead className="px-5 py-3 font-medium">Role</TableHead>
                  <TableHead className="px-5 py-3 font-medium">Status</TableHead>
                  <TableHead className="px-5 py-3 text-right font-medium">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {busy && !users.length && <TableRow><TableCell colSpan={4} className="py-8 text-center text-muted-foreground">Loading users…</TableCell></TableRow>}
                {users.map((user) => (
                  <TableRow key={user.nic} className="border-b last:border-0">
                    <TableCell className="px-4 py-3">
                      <p className="font-medium">{user.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {user.email}
                      </p><p className="mt-1 text-xs text-muted-foreground">NIC: {user.nic}</p>
                    </TableCell>

                    <TableCell className="px-4 py-3">{user.role}</TableCell>
                    <TableCell className="px-4 py-3">
                      <StatusBadge status={user.accountStatus} />
                    </TableCell>
                    <TableCell className="px-4 py-3 text-right">
                      {user.nic !== profile.nic && (
                        <div className="flex justify-end gap-2">
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busy || detailsBusy}
                            onClick={() => void openUserDetails(user)}
                          >
                            <UserRound className="size-4" /> View details
                          </Button>
                          <Button
                            variant="outline"
                            size="sm"
                            disabled={busy}
                            onClick={() => startEditingUser(user)}
                          >
                            <Pencil />
                            Edit
                          </Button>
                          <StatusActions
                            user={user}
                            busy={busy}
                            onChange={(nextStatus) =>
                              requestStatusChange(user, nextStatus)
                            }
                          />
                        </div>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
                {!busy && users.length === 0 && (
                  <TableRow>
                    <TableCell
                      colSpan={4}
                      className="px-5 py-8 text-center text-muted-foreground"
                    >
                      No users match these filters.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
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
      <Dialog
        open={confirmation !== null}
        onOpenChange={(open) => {
          if (!open && !busy) setConfirmation(null)
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirm account change</DialogTitle>
            <DialogDescription>{confirmation?.message}</DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button
              variant="outline"
              disabled={busy}
              onClick={() => setConfirmation(null)}
            >
              Cancel
            </Button>
            <Button
              disabled={busy || !confirmation}
              onClick={async () => {
                if (!confirmation) return
                await confirmation.action()
                setConfirmation(null)
              }}
            >
              {busy ? "Working..." : "Confirm"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
