// Supported application views/screens
export type View = "dashboard" | "users" | "stations" | "slots" | "reservations" | "transfers"

// Maps the current browser URL pathname to internal view key
export function viewFromPath(pathname: string): View {
  if (pathname === "/dashboard") return "dashboard"
  if (pathname === "/transfers") return "transfers"
  if (pathname === "/stations") return "stations"
  if (pathname === "/slots") return "slots"
  if (pathname === "/reservations") return "reservations"
  return "users"
}

// Returns the URL route string for a given view state
function pathForView(view: View): string {
  if (view === "dashboard") return "/dashboard"
  if (view === "transfers") return "/transfers"
  if (view === "stations") return "/stations"
  if (view === "slots") return "/slots"
  if (view === "reservations") return "/reservations"
  return "/users"
}

// Updates browser history without full page reload
export function navigateToView(view: View, replace = false): View {
  const nextPath = pathForView(view)
  if (window.location.pathname !== nextPath) {
    window.history[replace ? "replaceState" : "pushState"]({}, "", nextPath)
  }
  return view
}

// Generates breadcrumb label based on active view and user role
export function getBreadcrumbTitle(view: View, userRole?: string): string {
  switch (view) {
    case "dashboard": return "Dashboard"
    case "transfers":
      return "Transfers & monitoring"
    case "stations":
      return "Station management"
    case "slots":
      return "Slot management"
    case "reservations":
      return "Reservations"
    case "users":
    default:
      return userRole === "Backoffice" ? "User management" : "My account"
  }
}
