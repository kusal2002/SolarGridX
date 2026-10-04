import { useEffect, useState } from "react"
import { useAuth } from "@/context/AuthContext"
import { AuthPage } from "@/pages/AuthPage"
import { UserManagementPage } from "@/pages/UserManagementPage"
import { StationsPage } from "@/pages/StationsPage"
import { SlotsPage } from "@/pages/SlotsPage"
import { ReservationsPage } from "@/pages/ReservationsPage"
import { TransfersPage } from "@/pages/TransfersPage"
import { AccessDeniedPage } from "@/pages/AccessDeniedPage"
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
    const handleLogout = () => handleNavigate("users", true)
    window.addEventListener("popstate", handlePopState)
    window.addEventListener("solargridx:logout", handleLogout)
    return () => {
      window.removeEventListener("popstate", handlePopState)
      window.removeEventListener("solargridx:logout", handleLogout)
    }
  }, [])

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

  if (user.role === "Prosumer" && ["stations", "slots", "transfers"].includes(activeView)) {
    return <AccessDeniedPage onBack={() => handleNavigate("users", true)} />
  }

  return (
    <MainLayout user={user} activeView={activeView} onNavigate={handleNavigate} onLogout={() => { logout(); handleNavigate("users", true) }}>
      {activeView === "transfers" ? (
        <TransfersPage />
      ) : activeView === "stations" ? (
        <StationsPage />
      ) : activeView === "slots" ? (
        <SlotsPage />
      ) : activeView === "reservations" ? (
        <ReservationsPage />
      ) : (
        <UserManagementPage currentUser={user} onUserUpdated={updateUser} />
      )}
    </MainLayout>
  )
}
