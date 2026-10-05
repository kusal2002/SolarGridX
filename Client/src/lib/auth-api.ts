import type { LoginResponse, PagedUsers, User } from "@/types/user"

import { request } from "./api-client"

// Sends login credentials to receive JWT token and user info
export function login(email: string, password: string) {
  return request<LoginResponse>("/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
  })
}

// Registers a new prosumer account with pending status
export function register(data: { nic: string; name: string; email: string; password: string }) {
  return request<User>("/auth/register", {
    method: "POST",
    body: JSON.stringify({ NIC: data.nic, Name: data.name, Email: data.email, Password: data.password }),
  })
}

// Retrieves the profile of the currently authenticated user
export function getMyProfile() {
  return request<User>("/auth/me")
}

// Fetches user details by National Identity Card (NIC)
export function getUserByNic(nic: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}`)
}

// Retrieves paginated list of users with optional filtering and sorting
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

// Updates user name and email details
export function updateProfile(nic: string, name: string, email: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}/profile`, {
    method: "PUT",
    body: JSON.stringify({ Name: name, Email: email }),
  })
}

// Updates user account status (e.g., Active, Inactive)
export function updateUserStatus(nic: string, status: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}/status`, {
    method: "PATCH",
    body: JSON.stringify(status),
  })
}

// Backoffice endpoint to register a new Grid Operator or Backoffice staff member
export function createStaffUser(data: { nic: string; name: string; email: string; password: string; role: "Backoffice" | "Grid Operator" }) {
  return request<User>("/auth/staff", {
    method: "POST",
    body: JSON.stringify({ NIC: data.nic, Name: data.name, Email: data.email, Password: data.password, Role: data.role }),
  })
}

// Submits a request to deactivate the user's account
export function requestDeactivation(nic: string) {
  return request<User>(`/auth/users/${encodeURIComponent(nic)}/deactivation-request`, { method: "POST" })
}