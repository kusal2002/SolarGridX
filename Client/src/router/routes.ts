export type View = "users" | "stations" | "slots" | "reservations"

export function viewFromPath(pathname: string): View {
  if (pathname === "/stations") return "stations"
  if (pathname === "/slots") return "slots"
  if (pathname === "/reservations") return "reservations"
  return "users"
}

function pathForView(view: View): string {
  if (view === "stations") return "/stations"
  if (view === "slots") return "/slots"
  if (view === "reservations") return "/reservations"
  return "/users"
}

export function navigateToView(view: View, replace = false): View {
  const nextPath = pathForView(view)
  if (window.location.pathname !== nextPath) {
    window.history[replace ? "replaceState" : "pushState"]({}, "", nextPath)
  }
  return view
}

export function getBreadcrumbTitle(view: View, userRole?: string): string {
  switch (view) {
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
