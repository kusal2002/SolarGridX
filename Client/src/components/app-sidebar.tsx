"use client"

import * as React from "react"

import { NavMain } from "@/components/nav-main"
import { NavUser } from "@/components/nav-user"
import {
  Sidebar,
  SidebarContent,
  SidebarFooter,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"
import {
  Building2Icon,
  UserRoundCogIcon,
  TerminalIcon,
  CalendarCheck2Icon,
} from "lucide-react"
import type { User } from "@/types/user"
import type { View } from "@/router/routes"

export function AppSidebar({ currentUser, activeView, onNavigate, onLogout, ...props }: React.ComponentProps<typeof Sidebar> & { currentUser: User; activeView: View; onNavigate: (view: View) => void; onLogout: () => void }) {
  const navMain = [
    {
      title: currentUser.role === "Backoffice" ? "User Management" : "My Account",
      url: "users",
      icon: <UserRoundCogIcon />,
      isActive: activeView === "users",
    },
    ...(currentUser.role === "Backoffice" || currentUser.role === "Grid Operator" ? [{
      title: "Station Management",
      url: "stations",
      icon: <Building2Icon />,
      isActive: activeView === "stations" || activeView === "slots",
      items: [
        {
          title: "Stations",
          url: "stations",
        },
        {
          title: "Slots",
          url: "slots",
        },
      ],
    }] : []),
    {
      title: "Reservations",
      url: "reservations",
      icon: <CalendarCheck2Icon />,
      isActive: activeView === "reservations",
    },
  ]

  return (
    <Sidebar variant="inset" {...props}>
      <SidebarHeader>
        <SidebarMenu>
          <SidebarMenuItem>
            <SidebarMenuButton size="lg" render={<a href="#" />}>
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                <TerminalIcon className="size-4" />
              </div>
              <div className="grid flex-1 text-left text-sm leading-tight">
                <span className="truncate font-medium">Acme Inc</span>
                <span className="truncate text-xs">Enterprise</span>
              </div>
            </SidebarMenuButton>
          </SidebarMenuItem>
        </SidebarMenu>
      </SidebarHeader>
      <SidebarContent>
        <NavMain items={navMain} onNavigate={(view) => onNavigate(view as View)} />
      </SidebarContent>
      <SidebarFooter>
        <NavUser user={{ name: currentUser.name, email: currentUser.email, avatar: "" }} onLogout={onLogout} />
      </SidebarFooter>
    </Sidebar>
  )
}
