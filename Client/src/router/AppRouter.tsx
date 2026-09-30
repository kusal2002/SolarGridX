import { useEffect, useState } from "react"
import { useAuth } from "@/context/AuthContext"
import { AuthPage } from "@/pages/AuthPage"
import { UserManagementPage } from "@/pages/UserManagementPage"
import { StationsPage } from "@/pages/StationsPage"
import { ReservationsPage } from "@/pages/ReservationsPage"
import { MainLayout } from "@/components/layout/main-layout"
import { navigateToView, viewFromPath, type View } from "./routes"

export function AppRouter() {
  const { user, checkingSession, login, logout, updateUser } = useAuth()
  const [activeView, setActiveView] = useState<View>(() => viewFromPath(window.location.pathname))

  const handleNavigate = (view: View, replace = false) => {
    navigateToView(view, replace)
    setActiveView(view)
  }

  useEffect(() => {
    const handlePopState = () => setActiveView(viewFromPath(window.location.pathname))
    window.addEventListener("popstate", handlePopState)
    return () => window.removeEventListener("popstate", handlePopState)
  }, [])

  useEffect(() => {
    if (user) {
      const requestedView = viewFromPath(window.location.pathname)
      if (user.role === "Prosumer" && requestedView === "stations") {
        setTimeout(() => handleNavigate("users", true), 0)
      }
    }
  }, [user])

  if (checkingSession) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Checking your session...
      </div>
    )
  }

  if (!user) {
    return <AuthPage onAuthenticated={login} />
  }

  return (
    <MainLayout user={user} activeView={activeView} onNavigate={handleNavigate} onLogout={logout}>
      {activeView === "stations" ? (
        <StationsPage />
      ) : activeView === "reservations" ? (
        <ReservationsPage />
      ) : (
        <UserManagementPage currentUser={user} onUserUpdated={updateUser} />
      )}
    </MainLayout>
  )
}
