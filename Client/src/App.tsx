import { AuthProvider } from "@/context/AuthContext"
import { AppRouter } from "@/router/AppRouter"

export function App() {
  return (
    <AuthProvider>
      <AppRouter />
    </AuthProvider>
  )
}

export default App