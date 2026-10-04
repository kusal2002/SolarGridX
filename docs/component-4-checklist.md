# Component 4 quick check

## Start the apps

From the repository root:

```powershell
dotnet run --project SolarGridX --launch-profile http
```

In a second terminal:

```powershell
cd Client
npm ci
npm run dev
```

Open the Vite URL shown in the terminal. Sign in as Backoffice or Grid Operator and open **Transfers & Monitoring**.

Open `Mobile` in Android Studio, sync Gradle and run the app. Set `ApiClient.BASE_URL` to the API address your phone can reach, ending in `/api/`. For an emulator, the computer is `10.0.2.2`; for a physical phone, use the computer's LAN IP. The HTTP profile defaults to port 5084. For a phone, bind the API to the LAN interface (for example `dotnet run --project SolarGridX --launch-profile http --urls http://0.0.0.0:5084`) and allow that port through your local firewall.

## Happy path

1. Create two active Prosumer accounts, an active station/slot and a reservation. Approve the reservation through the existing staff reservation screen/API.
2. On Android, sign in as the reservation's prosumer. Open **Transfers**, select **Current**, open **Details**, then **Show QR / refresh**. The QR expires after 15 minutes.
3. On a second Android device, sign in as a Grid Operator. Open **Transfers**, enter the seller's NIC, tap **Scan QR**, then **Verify QR**. Grant camera permission when requested.
4. The API creates a Pending transfer, or verifies its existing Pending transfer. The buyer and kWh come from the reservation. Check the reservation ID and expected kWh in the result.
5. Tap **Start transfer**. Save a partial cumulative meter reading (for example 2 of 5 kWh). Enter the full expected reading (5) and tap **Complete**.
6. Refresh the prosumer screen and the web dashboard. The reservation should move into **History** as Completed. Open its web details to see create/verify/start/progress/complete audit events.

To test with one device, obtain the QR payload in Bruno using the prosumer token, then paste it into the web page or Android operator screen. The web page supports the same transfer actions.

## Bruno / API check

Use `Authorization: Bearer <token>` on every request.
The Bruno **Energy Transfers** folder includes requests 14–17 for QR, verification, dashboard and history. Set `prosumerToken` to the booking owner's token and copy the QR response's `payload` into `qrPayload`. Run request 15 before request 05 Start; request numbers are examples, not an unattended execution order.

| Request | Token / result |
| --- | --- |
| `GET /api/Reservation/{reservationId}/qr` | Owner's Prosumer token; returns `payload` and `expiresAt` |
| `POST /api/energy-transfers/verify` | Staff token; body below; returns verified transfer |
| `PATCH /api/energy-transfers/{transferId}/start` | Staff token; no body |
| `PATCH /api/energy-transfers/{transferId}/progress` | Staff token; `{ "transferredEnergyKWh": 2 }` |
| `PATCH /api/energy-transfers/{transferId}/complete` | Staff token; `{ "transferredEnergyKWh": 5 }` (use actual expected amount) |
| `GET /api/dashboard/summary` | Staff sees all bookings; Prosumer sees only their bookings |
| `GET /api/bookings/current`, `/pending`, `/history`, `/search` | Supports `search`, `status`, `stationId`, `from`, `to`, `page`, `pageSize` |

Verify body:

```json
{ "payload": "PASTE_QR_PAYLOAD", "sellerNIC": "ACTIVE_SELLER_NIC" }
```

## Rejection checks

- Alter the QR payload or paste random text: **400**.
- Wait 15 minutes and verify the old QR: **400**. Refresh it to continue.
- Pending/cancelled/completed reservation: QR generation or verification is rejected (**409**).
- Change a booking after issuing its QR: verification rejects the stale QR (**409**).
- Scan a completed booking's old QR or complete twice: rejected; no duplicate transfer/completion.
- Use a Prosumer token to verify/start/complete: **403**. Use no token: **401**.
- Request another prosumer's QR with a Prosumer token: **403**.
- Create a transfer with the old POST endpoint, then start without verification: **409**. Get a fresh QR and verify to continue.
- Use the same NIC for seller and buyer, an inactive seller, or a nonexistent seller: rejected.
- Decrease the meter reading, exceed the expected amount, or complete partially: **400**.
- Cancel a separate Pending transfer with a reason: capacity is restored once. Fail a separate InProgress transfer after saving its partial reading: only undelivered capacity is restored.

## Automated checks

```powershell
dotnet build SolarGridX/SolarGridX.csproj
dotnet run --project tests/SolarGridX.AuthChecks
dotnet run --project tests/SolarGridX.TransferChecks
cd Client
npm run build
cd ../Mobile
./gradlew.bat :app:assembleDebug
```

Optional database checks, from the repository root:

```powershell
dotnet run --project tests/SolarGridX.TransferChecks -- --integration
```

The integration runner uses configured MongoDB credentials (or `TRANSFER_TEST_MONGO`), creates a uniquely named disposable test database, and drops only that test database. It requires Atlas or a replica set and create/drop permissions. It checks transactions, concurrent creation, QR verification gating, duplicate completion, rollback and capacity accounting.

## Small implementation notes

- QR data uses ASP.NET Data Protection, contains no NIC, and is checked against the current reservation version. Keep the Data Protection key ring persistent and accessible to the IIS app identity. Multiple API instances must share their key ring and application name.
- Verification is recorded on the transfer (`verifiedBy`, `verifiedAt` and a history event). Once started, a transfer can be resumed from booking details; the QR expiry applies to verification, not to an already verified delivery.
- Dashboard future counts use Sri Lanka time. Booking list filtering and counts run centrally over the accessible reservations; this deliberately simple implementation is suitable for the team project, with database aggregation an option if the dataset grows.
- QR generation, verification and transfer actions require a connection to the API. Existing SQLite reservation caching remains available through the Trade tab.
