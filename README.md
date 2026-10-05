# SolarGridX - A Smart Solar Microgrid Energy Trading Platform

SolarGridX is a smart solar microgrid energy trading platform developed for the Enterprise Application Development (EAD) module. It combines account management, microgrid stations, energy reservations, and QR-verified energy transfers through a REST API, a web application, and an Android application.

## Main features

- **Identity and accounts:** Authentication, role-based authorization, prosumer registration, pending activation, profile management, and account deactivation/reactivation.
- **Microgrid stations:** Station creation, viewing, updating and deactivation, battery slots, schedules, availability, GPS/location data, and station discovery.
- **Energy reservations:** Booking creation, retrieval, updates and cancellation, with 7-day scheduling and 12-hour update/cancellation rules.
- **QR transactions:** Verification of approved bookings, Grid Operator validation, energy-transfer completion, and duplicate-completion prevention.
- **History and monitoring:** Booking history, search/filtering, and dashboards.

## Technology stack

| Component | Technologies |
| --- | --- |
| Backend | ASP.NET Core Web API, .NET 10, MongoDB, JWT authentication, BCrypt |
| Web application | React, TypeScript, Vite, Tailwind CSS, shadcn/ui, Leaflet |
| Android application | Kotlin, Jetpack Compose, Retrofit, OkHttp, MapLibre, ZXing |

## Repository structure

| Directory | Purpose |
| --- | --- |
| `SolarGridX/` | Backend API, controllers, services, and configuration |
| `Client/` | React web application |
| `Mobile/` | Native Android application |
| `SolarGridX-API/` | Component-specific API documentation and supporting files |
| `docs/` | Energy-transfer documentation and component checklist |
| `tests/` | Authentication and energy-transfer verification projects |

## Prerequisites

- .NET 10 SDK
- Node.js and npm compatible with Vite 8
- Access to the team's shared MongoDB Atlas cluster
- Android Studio and Android SDK 36 for mobile development

## Backend and web setup

Run the following backend commands from the repository root.

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

Open `http://localhost:5173` for the frontend. The API runs at `http://localhost:5084`. If `Client/.env` does not exist, copy `Client/.env.example` to it. Its local API setting should be:

```dotenv
VITE_API_URL=http://127.0.0.1:5084/api
```

The API requires a JWT signing key that must not be committed to source control. Configure it with User Secrets during local development:

```bash
dotnet user-secrets set "Jwt:Key" "replace-with-a-random-secret-at-least-32-bytes-long" --project SolarGridX/SolarGridX.csproj
```

Optional first Backoffice bootstrap values can also be supplied through User Secrets. The account is created once, only when the NIC or email does not already exist:

```bash
dotnet user-secrets set "BootstrapAdmin:NIC" "admin-nic" --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "BootstrapAdmin:Name" "System Administrator" --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "BootstrapAdmin:Email" "admin@example.com" --project SolarGridX/SolarGridX.csproj
dotnet user-secrets set "BootstrapAdmin:Password" "replace-with-a-strong-password" --project SolarGridX/SolarGridX.csproj
```

Changing a user's account status rotates their security stamp. Existing JWTs for that account are rejected on the next authenticated request, including after deactivation.

## Android setup

Open `Mobile/` in Android Studio, sync the Gradle project, and run the app on an emulator or an Android device running Android 10 (API 29) or later.

The mobile API endpoint is configured by `BASE_URL` in `Mobile/app/src/main/java/com/kusal/solargridxmobile/data/api/ApiClient.kt`. To connect an Android emulator to the local API, use `http://10.0.2.2:5084/api/`. For a physical device, use a backend address reachable from the device.

## Development checks

Run the backend verification projects from the repository root:

```bash
dotnet run --project tests/SolarGridX.AuthChecks/SolarGridX.AuthChecks.csproj
dotnet run --project tests/SolarGridX.TransferChecks/SolarGridX.TransferChecks.csproj
```

Run the web checks from `Client/`:

```bash
npm run lint
npm run build
```

## Team contributions

| Registration Number | Name | Contribution |
| --- | --- | --- |
| IT23308220 | Jayawardha R A T M | Developed identity, authentication and account management.<br><br>Implemented user authentication, role-based authorization, and account-status handling.<br><br>Developed prosumer/user management including registration, pending activation, profile management, deactivation and reactivation.<br><br>Developed REST APIs for login, identity, role authorization, user/prosumer management and account status.<br><br>**Report contribution:** Prepared the Data Flow Diagram (DFD) and Individual Contribution section, and added relevant source code and UI screenshots. |
| IT23321854 | I S Indrachapa | Developed microgrid node/station management, including creation, reading, updating and deactivation.<br><br>Implemented battery-slot, schedule and availability management through the Web API.<br><br>Developed station GPS/location data and Android map and station-discovery features.<br><br>Managed the microgrid and slot management component and its integration with other modules.<br><br>**Report contribution:** Prepared the Use Case Diagram and System Features section, and added relevant source code and UI screenshots. |
| IT23308466 | A D L Ransiika | Developed energy reservation management for creating, retrieving, updating and cancelling reservations.<br><br>Implemented reservation business rules, including the 7-day scheduling and 12-hour update/cancellation requirements.<br><br>Developed reservation-related Web API operations and booking workflow integration.<br><br>Implemented reservation web/mobile functionality for the reservation lifecycle.<br><br>**Report contribution:** Prepared the Database Design and Challenges sections, and added relevant source code and UI screenshots. |
| IT23311336 | Saparamadu M D K S | Developed QR transaction functionality for approved energy bookings.<br><br>Implemented server-side QR verification and validation for Grid Operator transactions.<br><br>Implemented energy-transfer completion and prevention of duplicate transaction completion.<br><br>Developed booking history, search/filtering and dashboard/monitoring functionality.<br><br>**Report contribution:** Added relevant source code and UI screenshots, and handled project setup, Git repository setup, and initial project configuration. |
