import type { LoginResponse, PagedUsers, User } from "@/types/user"

const API_URL = import.meta.env.VITE_API_URL

if (!API_URL) {
  throw new Error("VITE_API_URL is not configured.")
}

async function request<T>(path: string, options: RequestInit = {}) {
  const token = localStorage.getItem("solargridx-token")
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (!response.ok) {
    if (response.status === 401) {
      localStorage.removeItem("solargridx-token")
      localStorage.removeItem("solargridx-user")
      window.dispatchEvent(new Event("solargridx:logout"))
    }
    const body = await response.json().catch(() => ({}))
    throw new Error(body.message || "The request could not be completed.")
  }

  return response.json() as Promise<T>
}

export function login(email: string, password: string) {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  })
}

export function register(data: { nic: string; name: string; email: string; password: string }) {
  return request<User>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ NIC: data.nic, Name: data.name, Email: data.email, Password: data.password }),
  })
}

export function getMyProfile() {
  return request<User>("/auth/me")
}

export function getUsers(
  status?: string,
  role?: string,
  search = "",
  page = 1,
  pageSize = 10,
  sortBy = "createdAt",
  sortDirection = "desc"
) {
  const params = new URLSearchParams()
  params.set("page", String(page))
  params.set("pageSize", String(pageSize))
  params.set("sortBy", sortBy)
  params.set("sortDirection", sortDirection)
  if (search.trim()) params.set("search", search.trim())
  if (status && status !== "All") params.set("status", status)
  if (role && role !== "All") params.set("role", role)
  const query = params.toString()
  return request<PagedUsers>(`/auth/users?${query}`)
}

export function updateProfile(nic: string, name: string, email: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}/profile`, {
    method: "PUT",
    body: JSON.stringify({ Name: name, Email: email }),
  })
}

export function updateUserStatus(nic: string, status: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}/status`, {
    method: "PATCH",
    body: JSON.stringify(status),
  })
}

export function createStaffUser(data: { nic: string; name: string; email: string; password: string; role: "Backoffice" | "Grid Operator" }) {
  return request<User>("/auth/staff", {
    method: "POST",
    body: JSON.stringify({ NIC: data.nic, Name: data.name, Email: data.email, Password: data.password, Role: data.role }),
  })
}

export function requestDeactivation(nic: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}/deactivation-request`, { method: "POST" })
}