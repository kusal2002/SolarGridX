# SolarGridX - A Smart Solar Microgrid Energy Trading Platform

## Backend authentication setup

The API requires a JWT signing key that must not be committed to source control. Configure it with User Secrets during local development:

```bash
dotnet user-secrets set "Jwt:Key" "replace-with-a-long-random-secret" --project SolarGridX/SolarGridX.csproj
```

Optional first Backoffice bootstrap values can also be supplied through User Secrets. The account is created once, only when the NIC or email does not already exist:

```bash
dotnet user-secrets set "BootstrapAdmin:NIC" "admin-nic" --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "BootstrapAdmin:Name" "System Administrator" --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "BootstrapAdmin:Email" "admin@example.com" --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "BootstrapAdmin:Password" "replace-with-a-strong-password" --project SolarGridX/SolarGridX.csproj
```

Changing a user's account status rotates their security stamp. Existing JWTs for that account are rejected on the next authenticated request, including after deactivation.

<!-- This is a template for a new Vite project with React, TypeScript, and shadcn/ui.

## Adding components

To add components to your app, run the following command:

```bash
npx shadcn@latest add button
```

This will place the ui components in the `src/components` directory.

## Using components

To use the components in your app, import them as follows:

```tsx
import { Button } from "@/components/ui/button";
``` -->
