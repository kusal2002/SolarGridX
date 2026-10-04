import { ShieldX } from "lucide-react"
import { Button } from "@/components/ui/button"

export function AccessDeniedPage({ onBack }: { onBack: () => void }) {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <section className="w-full max-w-md rounded-xl border bg-card p-8 text-center shadow-sm">
        <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-red-100 text-red-700">
          <ShieldX className="size-6" />
        </div>
        <h1 className="mt-5 text-2xl font-semibold tracking-tight">Access denied</h1>
        <p className="mt-2 text-sm leading-6 text-muted-foreground">
          You do not have permission to access this page.
        </p>
        <Button className="mt-6" onClick={onBack}>
          Return to my account
        </Button>
      </section>
    </main>
  )
}
