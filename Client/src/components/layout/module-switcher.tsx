import { Button } from "@/components/ui/button"
import { Building2, CalendarCheck2 } from "lucide-react"
import type { View } from "@/router/routes"

interface ModuleSwitcherProps {
  activeView: View
  onNavigate: (view: View) => void
}

export function ModuleSwitcher({ activeView, onNavigate }: ModuleSwitcherProps) {
  return (
    <div className="flex items-center gap-1.5 rounded-lg border bg-muted/40 p-1">
      <Button
        variant={activeView === "reservations" ? "default" : "ghost"}
        size="sm"
        className="h-8 gap-1.5 text-xs"
        onClick={() => onNavigate("reservations")}
      >
        <CalendarCheck2 className="h-3.5 w-3.5" />
        Reservations (Member 3)
      </Button>
      <Button
        variant={activeView === "stations" ? "default" : "ghost"}
        size="sm"
        className="h-8 gap-1.5 text-xs"
        onClick={() => onNavigate("stations")}
      >
        <Building2 className="h-3.5 w-3.5" />
        Stations (Member 2)
      </Button>
    </div>
  )
}
