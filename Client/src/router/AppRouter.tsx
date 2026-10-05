import { useEffect, useState } from "react"
import { useAuth } from "@/context/AuthContext"
import { AuthPage } from "@/pages/AuthPage"
import { UserManagementPage } from "@/pages/UserManagementPage"
import { StationsPage } from "@/pages/StationsPage"
import { SlotsPage } from "@/pages/SlotsPage"
import { ReservationsPage } from "@/pages/ReservationsPage"
import { TransfersPage } from "@/pages/TransfersPage"
import { DashboardPage } from "@/pages/DashboardPage"
import { AccessDeniedPage } from "@/pages/AccessDeniedPage"
import { MainLayout } from "@/components/layout/main-layout"
import { navigateToView, viewFromPath, type View } from "./routes"

// Main application router that controls authentication state, role-based access, and view switching
export function AppRouter() {
  const { user, checkingSession, login, logout, updateUser } = useAuth()
  // Active view state initialized from current window URL path
  const [activeView, setActiveView] = useState<View>(() => viewFromPath(window.location.pathname))

  // Updates browser URL and switches the current page view
  const handleNavigate = (view: View, replace = false) => {
    navigateToView(view, replace)
    setActiveView(view)
  }

  // Listens for browser back/forward buttons and global logout events
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

  // Show loading indicator while session token is being checked
  if (checkingSession) {
    return (
      <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">
        Checking your session...
      </div>
    )
  }

  // Redirect unauthenticated visitors to login/register page
  if (!user) {
    return <AuthPage onAuthenticated={login} />
  }

  // Enforce role-based access guard: restrict Prosumers from staff management pages
  if (user.role === "Prosumer" && ["stations", "slots", "transfers", "dashboard"].includes(activeView)) {
    return <AccessDeniedPage onBack={() => handleNavigate("users", true)} />
  }

  // Render main layout shell with active page content
  return (
    <MainLayout user={user} activeView={activeView} onNavigate={handleNavigate} onLogout={() => { logout(); handleNavigate("users", true) }}>
      {activeView === "dashboard" ? <DashboardPage /> : activeView === "transfers" ? (
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
