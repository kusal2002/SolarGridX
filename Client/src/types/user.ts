// User role and account status union types
export type UserRole = "Prosumer" | "Backoffice" | "Grid Operator" | string

export type AccountStatus =
  | "Active"
  | "Inactive"
  | "Pending"
  | "DeactivationRequested"
  | string

// Data model representing a registered system user
export interface User {
  nic: string
  name: string
  email: string
  role: UserRole
  accountStatus: AccountStatus
  createdAt: string
  deactivationRequestedAt?: string | null
}

// Authentication response containing user profile and JWT token
export interface LoginResponse extends User {
  token: string
}

// Paginated API response structure for user listings
export interface PagedUsers {
  items: User[]
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
}