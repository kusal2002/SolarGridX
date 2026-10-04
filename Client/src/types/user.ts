export type UserRole = "Prosumer" | "Backoffice" | "Grid Operator" | string

export type AccountStatus =
  | "Active"
  | "Inactive"
  | "Pending"
  | "DeactivationRequested"
  | string

export interface User {
  nic: string
  name: string
  email: string
  role: UserRole
  accountStatus: AccountStatus
  createdAt: string
  deactivationRequestedAt?: string | null
}

export interface LoginResponse extends User {
  token: string
}

export interface PagedUsers {
  items: User[]
  page: number
  pageSize: number
  totalCount: number
  totalPages: number
}