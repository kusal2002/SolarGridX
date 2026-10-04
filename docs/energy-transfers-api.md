# EnergyTransfers backend API

Base URL: `http://localhost:5084` (HTTP launch profile) or `https://localhost:7172`.
All endpoints require `Authorization: Bearer <token>` for an active **Backoffice** or **Grid Operator** account. Log in with `POST /api/auth/login`. Prosumers cannot call these operational endpoints.

| Method | Path | Purpose / success |
| --- | --- | --- |
| POST | `/api/energy-transfers` | Create Pending transfer; 201 with Location header |
| GET | `/api/energy-transfers` | Filtered/paginated array; 200 |
| GET | `/api/energy-transfers/{id}` | Transfer details including audit history; 200 |
| GET | `/api/energy-transfers/{id}/history` | Ordered audit events; 200 |
| PATCH | `/api/energy-transfers/{id}/start` | Pending -> InProgress; 200 |
| PATCH | `/api/energy-transfers/{id}/progress` | Record cumulative delivered kWh; 200 |
| PATCH | `/api/energy-transfers/{id}/complete` | InProgress -> Completed; 200 |
| PATCH | `/api/energy-transfers/{id}/cancel` | Pending -> Cancelled; 200 |
| PATCH | `/api/energy-transfers/{id}/fail` | InProgress -> Failed; 200 |

## Create

```json
{
  "reservationId": "507f1f77bcf86cd799439011",
  "sellerId": "SELLER_NIC",
  "buyerId": "BUYER_NIC",
  "expectedEnergyKWh": 5.0
}
```

Replace the example ID with an actual approved reservation ID. Seller and buyer are **NIC strings from Users**, not MongoDB ObjectIds. Both must be distinct, active Prosumers. This implementation treats the reservation prosumer as the **buyer**. Expected energy must equal the reservation's requested energy and be between 0.001 and 1,000,000 kWh. The station and slot must be active at creation and start. One transfer is allowed per reservation for its entire lifetime, including failed/cancelled transfers; make a new reservation for a new attempt.

The response keeps the existing camel-case fields (`id`, `reservationId`, `sellerId`, `buyerId`, `expectedEnergyKWh`, `transferredEnergyKWh`, `status`, `createdAt`, `startedAt`, `completedAt`) and adds `updatedAt`, `endedAt`, `reason`, and `history`. Dates are UTC. History records action, resulting status, cumulative kWh, timestamp, actor NIC from the JWT, and reason. Clients cannot set status, timestamps, or audit identity through request DTOs.

## Start, meter readings, completion and failure

Start requires no body. Progress and completion require:

```json
{ "transferredEnergyKWh": 5.0 }
```

Readings are cumulative, not increments. They cannot decrease or exceed expected energy. Sending the same progress reading again is safe and does not add another audit event. Completion requires the full expected amount; it also marks the reservation Completed. For an interrupted delivery, record the last measured amount with progress, then fail the transfer. Meter readings are staff supplied; this module does not communicate with physical meters.

Cancel and fail require:

```json
{ "reason": "Delivery interrupted by a station fault" }
```

A nonblank reason of at most 500 characters is required. Cancellation is only allowed before start; failure is only allowed after start. Both close the reservation as Cancelled. Cancellation restores all reserved energy; failure restores only undelivered energy. Completion leaves consumed capacity deducted. All terminal states are immutable. Repeated terminal actions return 409, preventing capacity from being released twice. There is no delete or unrestricted status-edit endpoint.

Transfer start is an explicit staff operation. The module does not automatically start at a slot time or enforce a slot-time execution window.

Start and completion now require server QR verification. Obtain a fresh QR from `GET /api/Reservation/{id}/qr`, then staff call `POST /api/energy-transfers/verify` with `{ "payload": "...", "sellerNIC": "..." }`. Verification creates or verifies the Pending transfer and adds a verify audit event. See [Component 4 quick check](component-4-checklist.md) for the web, Android and Bruno walkthrough. For the older manual Bruno sequence below, verify after Create and before Start.

## List and errors

Optional query parameters: `status` (Pending/InProgress/Completed/Cancelled/Failed), `reservationId`, `sellerId`, `buyerId`, `page` (default 1), `pageSize` (default 50, max 100). Results are newest first, with ID as the ordering tie-breaker. The response remains an array for compatibility; request subsequent pages until fewer than pageSize records are returned.

Business errors return `{ "message": "..." }`. DTO validation failures use ASP.NET validation problem details.

- 400: invalid ID/body, mismatched buyer/energy, decreasing or excessive meter reading.
- 401: missing, expired or revoked token.
- 403: authenticated role has no access.
- 404: transfer, reservation or participant not found.
- 409: duplicate transfer, invalid lifecycle state, inactive participant/station/slot, or inconsistent linked reservation.

Reservations linked to transfers cannot be edited, cancelled, or changed through reservation endpoints. `PATCH /api/Reservation/{id}/status` now only permits Pending -> Approved. Use transfer completion to mark a linked reservation Completed. Reservation edit/cancel guards use the existing 400 response; the status endpoint returns 409 for invalid transitions.

## Database and existing data

The service creates the unique `ux_transfer_reservation` index at startup. Its case-insensitive comparison prevents uppercase/lowercase ObjectId strings from bypassing uniqueness. Existing duplicate reservation IDs cause startup to fail: inspect and reconcile them before restarting. No existing records are automatically deleted or rewritten.

Old transfers created before this implementation remain readable, but lifecycle operations require a consistent reservation `transferId` and reservation status (Approved for Pending transfer, InProgress for active transfer). Review their participants, amount, history, duplicate status, and reserved slot capacity before manually linking legacy data. Empty legacy history is returned as an empty array.

Transfer creation and lifecycle writes use MongoDB transactions, including the reservation and capacity changes. Reservation mutations also use transactions to prevent stale edits from overwriting transfer state. **MongoDB Atlas or a replica set is required**; standalone MongoDB does not support this workflow. See [MongoDB's C# transaction documentation](https://www.mongodb.com/docs/drivers/csharp/current/crud/transactions/).

## Bruno walkthrough

Open the existing `SolarGridX-API` collection and use the **Energy Transfers** folder. Add these environment variables without committing tokens:

| Variable | Value |
| --- | --- |
| baseUrl | `http://localhost:5084` |
| backofficeToken | Token from staff login (a Grid Operator token also works) |
| reservationId | Approved reservation ObjectId |
| sellerNIC / buyerNIC | Distinct active Prosumer NICs; buyer owns reservation |
| expectedEnergyKWh | Reservation amount, for example `5` |
| progressEnergyKWh | Partial cumulative amount, for example `2` |
| transferId | `id` copied from request 01 response |
| cancelTransferId | A separate freshly created Pending transfer |
| failTransferId | A separate started transfer, optionally with recorded progress |

1. Create two active Prosumer accounts, an active station/slot, and a reservation through the existing APIs. Approve the reservation via `PATCH /api/Reservation/{id}/status` with `{ "status": "Approved" }`.
2. Run 01 Create, then copy its `id` to transferId. Run 02: expect 409 for duplicate creation.
3. Run 03 through 10 in order. Progress records a partial amount; Complete sends the full expected amount. Verify the reservation is now Completed.
4. Use separate reservations/transfers for 11 (cancel) and 12 (fail). Check capacity and terminal states. Run 13 for malformed ID validation.
5. Also try missing authorization (401), Prosumer token (403), unapproved reservation (409), wrong buyer (400), decreasing progress (400), and repeated completion (409).

Requests are manual examples with expected statuses in their names; they do not automatically log in, seed data, extract IDs, or assert responses. They should not be run as an unattended whole-folder sequence without fixtures.

## Automated verification

Run from the repository root:

```powershell
dotnet build SolarGridX/SolarGridX.csproj
dotnet run --project tests/SolarGridX.AuthChecks
dotnet run --project tests/SolarGridX.TransferChecks
dotnet run --project tests/SolarGridX.TransferChecks -- --integration
```

The last command uses application MongoDB configuration (including user secrets), or `TRANSFER_TEST_MONGO` if set. It creates a unique `sgx_transfer_<ObjectId>` database and drops that exact database in finally. The account must allow creating/dropping this disposable database. It never targets the configured business database. Integration checks cover concurrent creation/cancellation, lifecycle, reservation guards, transaction rollback and capacity accounting. The non-integration runner checks DTO validation and all state/action combinations without MongoDB.
