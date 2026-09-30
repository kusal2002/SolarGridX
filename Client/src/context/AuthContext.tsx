import { createContext, useContext, useEffect, useState, type ReactNode } from "react"
import { getMyProfile } from "@/lib/auth-api"
import type { LoginResponse, User } from "@/types/user"

interface AuthContextType {
  user: User | null
  checkingSession: boolean
  login: (data: LoginResponse) => void
  logout: () => void
  updateUser: (user: User) => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [checkingSession, setCheckingSession] = useState(() => Boolean(localStorage.getItem("solargridx-token")))

  useEffect(() => {
    const handleLogoutEvent = () => {
      setUser(null)
      setCheckingSession(false)
    }

    window.addEventListener("solargridx:logout", handleLogoutEvent)

    const token = localStorage.getItem("solargridx-token")
    if (!token) {
      return () => {
        window.removeEventListener("solargridx:logout", handleLogoutEvent)
      }
    }

    void getMyProfile()
      .then((profile) => {
        localStorage.setItem("solargridx-user", JSON.stringify(profile))
        setUser(profile)
      })
      .catch(() => {
        localStorage.removeItem("solargridx-token")
        localStorage.removeItem("solargridx-user")
        setUser(null)
      })
      .finally(() => setCheckingSession(false))

    return () => {
      window.removeEventListener("solargridx:logout", handleLogoutEvent)
    }
  }, [])

  const login = (data: LoginResponse) => {
    localStorage.setItem("solargridx-token", data.token)
    localStorage.setItem("solargridx-user", JSON.stringify(data))
    setUser(data)
  }

  const logout = () => {
    localStorage.removeItem("solargridx-token")
    localStorage.removeItem("solargridx-user")
    setUser(null)
  }

  const updateUser = (updatedUser: User) => {
    localStorage.setItem("solargridx-user", JSON.stringify(updatedUser))
    setUser(updatedUser)
  }

  return (
    <AuthContext.Provider value={{ user, checkingSession, login, logout, updateUser }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider")
  }
  return context
}
