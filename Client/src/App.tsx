import { useEffect, useState } from "react"
import { SidebarProvider } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { SidebarInset, SidebarTrigger } from "@/components/ui/sidebar"
import { Separator } from "@/components/ui/separator"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"

import { AuthPage } from "./pages/AuthPage"
import { UserManagementPage } from "./pages/UserManagementPage"
import { StationsPage } from "./pages/StationsPage"
import { getMyProfile } from "@/lib/auth-api"
import type { LoginResponse, User } from "@/types/user"

type View = "users" | "stations"

function viewFromPath(pathname: string): View {
  return pathname === "/stations" ? "stations" : "users"
}

function pathForView(view: View) {
  return view === "stations" ? "/stations" : "/users"
}

export function App() {
  const [user, setUser] = useState<User | null>(null)
  const [checkingSession, setCheckingSession] = useState(() => Boolean(localStorage.getItem("solargridx-token")))
  const [activeView, setActiveView] = useState<View>(() => viewFromPath(window.location.pathname))

  const navigateTo = (view: View, replace = false) => {
    const nextPath = pathForView(view)
    if (window.location.pathname !== nextPath) {
      window.history[replace ? "replaceState" : "pushState"]({}, "", nextPath)
    }
    setActiveView(view)
  }

  useEffect(() => {
    const handleLogout = () => {
      setUser(null)
      setCheckingSession(false)
      navigateTo("users", true)
    }
    window.addEventListener("solargridx:logout", handleLogout)

    const handlePopState = () => setActiveView(viewFromPath(window.location.pathname))
    window.addEventListener("popstate", handlePopState)

    const token = localStorage.getItem("solargridx-token")
    if (!token) {
      return () => {
        window.removeEventListener("solargridx:logout", handleLogout)
        window.removeEventListener("popstate", handlePopState)
      }
    }

    void getMyProfile()
      .then((profile) => {
        localStorage.setItem("solargridx-user", JSON.stringify(profile))
        setUser(profile)
        const requestedView = viewFromPath(window.location.pathname)
        navigateTo(profile.role === "Prosumer" && requestedView === "stations" ? "users" : requestedView, true)
      })
      .catch(() => {
        localStorage.removeItem("solargridx-token")
        localStorage.removeItem("solargridx-user")
      })
      .finally(() => setCheckingSession(false))

    return () => {
      window.removeEventListener("solargridx:logout", handleLogout)
      window.removeEventListener("popstate", handlePopState)
    }
  }, [])

  const handleAuthenticated = (result: LoginResponse) => {
    localStorage.setItem("solargridx-token", result.token)
    localStorage.setItem("solargridx-user", JSON.stringify(result))
    setUser(result)
    const requestedView = viewFromPath(window.location.pathname)
    navigateTo(result.role === "Prosumer" && requestedView === "stations" ? "users" : requestedView, true)
  }

  const handleLogout = () => {
    localStorage.removeItem("solargridx-token")
    localStorage.removeItem("solargridx-user")
    setUser(null)
    navigateTo("users", true)
  }

  if (checkingSession) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted-foreground">Checking your session...</div>
  }

  if (!user) {
    return <AuthPage onAuthenticated={handleAuthenticated} />
  }

  return (
    <SidebarProvider>
      <AppSidebar currentUser={user} activeView={activeView} onNavigate={navigateTo} onLogout={handleLogout} />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center gap-2">
          <div className="flex items-center gap-2 px-4">
            <SidebarTrigger className="-ml-1" />
            <Separator
              orientation="vertical"
              className="mr-2 data-[orientation=vertical]:h-4"
            />
            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem className="hidden md:block">
                  <BreadcrumbLink href="#">
                    SolarGridX
                  </BreadcrumbLink>
                </BreadcrumbItem>
                <BreadcrumbSeparator className="hidden md:block" />
                <BreadcrumbItem>
                  <BreadcrumbPage>{activeView === "stations" ? "Station management" : user.role === "Backoffice" ? "User management" : "My account"}</BreadcrumbPage>
                </BreadcrumbItem>
              </BreadcrumbList>
            </Breadcrumb>
          </div>
        </header>
        {activeView === "stations" ? <StationsPage /> : <UserManagementPage currentUser={user} onUserUpdated={(updatedUser) => {
          localStorage.setItem("solargridx-user", JSON.stringify(updatedUser))
          setUser(updatedUser)
        }} />}
      </SidebarInset>
    </SidebarProvider>
  )
}

export default App
