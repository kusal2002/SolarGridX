import { useState } from "react"
import { LogIn, ShieldCheck, UserPlus } from "lucide-react"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { login, register } from "@/lib/auth-api"
import type { LoginResponse } from "@/types/user"

export function AuthPage({ onAuthenticated }: { onAuthenticated: (user: LoginResponse) => void }) {
  const [mode, setMode] = useState<"login" | "register">("login")
  const [form, setForm] = useState({ nic: "", name: "", email: "", password: "" })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState("")
  const [notice, setNotice] = useState("")

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError("")
    setNotice("")
    try {
      if (mode === "login") {
        onAuthenticated(await login(form.email, form.password))
      } else {
        const result = await register(form)
        setNotice(`Registration received for ${result.email}. A backoffice user must activate the account before you can sign in.`)
        setMode("login")
        setForm({ nic: "", name: "", email: form.email, password: "" })
      }
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : "Unable to complete the request.")
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="grid min-h-screen lg:grid-cols-[1.05fr_0.95fr]">
      <section className="relative hidden overflow-hidden bg-slate-950 p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <div className="absolute inset-0 bg-[radial-gradient(circle_at_20%_20%,rgba(52,211,153,0.25),transparent_35%),linear-gradient(135deg,#082f49,#0f172a_70%)]" />
        <div className="relative flex items-center gap-3 text-sm font-medium tracking-wide"><span className="flex size-9 items-center justify-center rounded-lg bg-emerald-400 text-slate-950"><img src="/solargridx-mark.svg" alt="" className="size-9" /></span> SOLARGRIDX</div>
        <div className="relative max-w-lg"><p className="mb-5 text-sm font-medium uppercase tracking-[0.24em] text-emerald-300">Energy, coordinated</p><h1 className="text-5xl font-semibold tracking-tight">A clearer way to manage your place in the grid.</h1><p className="mt-6 max-w-md text-lg leading-8 text-slate-300">Connect producers, operators, and the stations that keep local energy moving.</p></div>
        <p className="relative text-sm text-slate-400">Secure access for every grid participant.</p>
      </section>
      <section className="flex items-center justify-center bg-background p-6 sm:p-10">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden"><div className="flex items-center gap-2 font-semibold"><img src="/solargridx-mark.svg" alt="" className="size-9" /> SOLARGRIDX</div></div>
          <div className="mb-8"><p className="mb-2 text-sm font-medium text-emerald-600">{mode === "login" ? "Welcome back" : "Join the grid"}</p><h2 className="text-3xl font-semibold tracking-tight">{mode === "login" ? "Sign in to your workspace" : "Create your account"}</h2><p className="mt-2 text-sm text-muted-foreground">{mode === "login" ? "Use your registered email and password." : "Register as a prosumer to get started."}</p></div>
          <form onSubmit={submit} className="space-y-4">
            {mode === "register" && <><div className="space-y-2"><Label htmlFor="nic">NIC</Label><Input id="nic" autoComplete="off" required value={form.nic} onChange={(e) => setForm({ ...form, nic: e.target.value })} /></div><div className="space-y-2"><Label htmlFor="name">Full name</Label><Input id="name" autoComplete="name" required value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></div></>}
            <div className="space-y-2"><Label htmlFor="email">Email</Label><Input id="email" type="email" autoComplete="email" required value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></div>
            <div className="space-y-2"><Label htmlFor="password">Password</Label><Input id="password" type="password" autoComplete={mode === "register" ? "new-password" : "current-password"} minLength={mode === "register" ? 8 : undefined} required value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></div>
            {error && <p className="rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-600">{error}</p>}
            {notice && <p className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-sm text-emerald-700">{notice}</p>}
            <Button type="submit" className="w-full" disabled={busy}>{mode === "login" ? <LogIn /> : <UserPlus />}{busy ? "Working..." : mode === "login" ? "Sign in" : "Create account"}</Button>
          </form>
          <button className="mt-6 flex w-full items-center justify-center gap-2 text-sm text-muted-foreground hover:text-foreground" onClick={() => { setMode(mode === "login" ? "register" : "login"); setError(""); setNotice("") }}><ShieldCheck className="size-4" />{mode === "login" ? "New to SolarGridX? Register" : "Already have an account? Sign in"}</button>
        </div>
      </section>
    </main>
  )
}