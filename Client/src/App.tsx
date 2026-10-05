import { AuthProvider } from "@/context/AuthContext"
import { AppRouter } from "@/router/AppRouter"

// Root application component wrapping the app with AuthProvider and router
export function App() {
  return (
    <AuthProvider>
      <AppRouter />
    </AuthProvider>
  )
}

export default App
