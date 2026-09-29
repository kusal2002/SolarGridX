import { useState } from "react"
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
import { Building2, CalendarCheck2 } from "lucide-react"
import { Button } from "@/components/ui/button"

import { StationsPage } from "./pages/StationsPage"
import { ReservationsPage } from "./pages/ReservationsPage"

export function App() {
  const [activeTab, setActiveTab] = useState<"stations" | "reservations">(
    "reservations"
  )

  return (
    <SidebarProvider>
      <AppSidebar />
      <SidebarInset>
        <header className="flex h-16 shrink-0 items-center justify-between gap-2 border-b px-4">
          <div className="flex items-center gap-2">
            <SidebarTrigger className="-ml-1" />
            <Separator
              orientation="vertical"
              className="mr-2 data-[orientation=vertical]:h-4"
            />
            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem className="hidden md:block">
                  <BreadcrumbLink href="#">SolarGridX</BreadcrumbLink>
                </BreadcrumbItem>
                <BreadcrumbSeparator className="hidden md:block" />
                <BreadcrumbItem>
                  <BreadcrumbPage>
                    {activeTab === "stations"
                      ? "Station Management"
                      : "Energy Reservations"}
                  </BreadcrumbPage>
                </BreadcrumbItem>
              </BreadcrumbList>
            </Breadcrumb>
          </div>

          {/* Quick Module Switcher */}
          <div className="flex items-center gap-1.5 rounded-lg border bg-muted/40 p-1">
            <Button
              variant={activeTab === "reservations" ? "default" : "ghost"}
              size="sm"
              className="h-8 gap-1.5 text-xs"
              onClick={() => setActiveTab("reservations")}
            >
              <CalendarCheck2 className="h-3.5 w-3.5" />
              Reservations (Member 3)
            </Button>
            <Button
              variant={activeTab === "stations" ? "default" : "ghost"}
              size="sm"
              className="h-8 gap-1.5 text-xs"
              onClick={() => setActiveTab("stations")}
            >
              <Building2 className="h-3.5 w-3.5" />
              Stations (Member 2)
            </Button>
          </div>
        </header>

        <main className="flex-1 overflow-auto">
          {activeTab === "stations" ? <StationsPage /> : <ReservationsPage />}
        </main>
      </SidebarInset>
    </SidebarProvider>
  )
}

export default App
