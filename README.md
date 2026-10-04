# SolarGridX - A Smart Solar Microgrid Energy Trading Platform

## Backend authentication setup

## Shared team database setup

All team members use the same MongoDB Atlas cluster and database. Do not create a separate database for each member. Each member configures the shared connection string locally with User Secrets; credentials are not committed to Git:

```bash
dotnet user-secrets set "MongoDbSettings:ConnectionString" 'TEAM_MONGODB_CONNECTION_STRING' --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "MongoDbSettings:DatabaseName" "SolarGridXDB" --project SolarGridX/SolarGridX.csproj
```

The first Backoffice account is created by the bootstrap settings below. Once that account exists in the shared database, other members can use the same Backoffice email and password; they do not need to create another admin account. Existing Backoffice users can create additional Backoffice or Grid Operator accounts from the User Management screen.

Start the API with:

```bash
dotnet run --project SolarGridX/SolarGridX.csproj --launch-profile http
```

Start the client in a second terminal:

```bash
cd Client
npm install
npm run dev
```

Open `http://localhost:5173` for the frontend. The API runs at `http://localhost:5084`.

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
