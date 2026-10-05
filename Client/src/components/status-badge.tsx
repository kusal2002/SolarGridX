import { Badge } from "@/components/ui/badge"
import { CheckCircle2, Clock3, CircleOff, Activity, XCircle } from "lucide-react"

// Reusable badge component that displays entity status with appropriate color and icon
export function StatusBadge({ status }: { status: string }) {
  const success = ["Active", "Approved", "Completed"].includes(status)
  const pending = ["Pending", "DeactivationRequested"].includes(status)
  const progress = status === "InProgress"
  const Icon = success ? CheckCircle2 : pending ? Clock3 : progress ? Activity : status === "Failed" ? XCircle : CircleOff
  const color = success ? "border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-400" : pending ? "border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-400" : progress ? "border-blue-500/20 bg-blue-500/10 text-blue-700 dark:text-blue-400" : "border-border bg-muted text-muted-foreground"
  return <Badge className={color}><Icon className="size-3.5" />{status === "InProgress" ? "In progress" : status === "DeactivationRequested" ? "Deactivation requested" : status}</Badge>
}
