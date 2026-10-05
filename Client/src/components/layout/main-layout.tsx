import type { ReactNode } from "react"
import { SidebarProvider, SidebarInset } from "@/components/ui/sidebar"
import { AppSidebar } from "@/components/app-sidebar"
import { AppHeader } from "./app-header"
import type { User } from "@/types/user"
import type { View } from "@/router/routes"

interface MainLayoutProps {
  user: User
  activeView: View
  onNavigate: (view: View) => void
  onLogout: () => void
  children: ReactNode
}

// Main application layout shell containing the sidebar, header, and content area
export function MainLayout({ user, activeView, onNavigate, onLogout, children }: MainLayoutProps) {
  return (
    <SidebarProvider>
      <AppSidebar currentUser={user} activeView={activeView} onNavigate={onNavigate} onLogout={onLogout} />
      <SidebarInset>
        <AppHeader activeView={activeView} userRole={user.role} />
        {children}
      </SidebarInset>
    </SidebarProvider>
  )
}
