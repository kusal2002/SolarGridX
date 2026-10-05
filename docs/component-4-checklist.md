# Component 4 quick check

Test accounts: one active Prosumer (reservation owner) and one Grid Operator. The reservation must reference an active station and slot. No seller account is needed.

1. Approve the reservation from the web Reservations page. Approved means booking accepted, not energy delivered.
2. As the owner on Android, open Transfers → Current → Details → Show QR / refresh.
3. As the operator, open Transfers → Scan QR. The scan verifies automatically. Check the displayed prosumer NIC, station, slot and expected kWh.
4. Tap Start transfer. The reservation becomes InProgress.
5. Save a cumulative kWh reading, then enter the full reserved kWh and Complete. Check both transfer and reservation are Completed.

The web Transfers page also scans using its camera. Allow camera access and use localhost or HTTPS. Raw QR text is used only in Bruno API requests. With one phone, display its QR screenshot on another screen, then sign in as operator and scan it before the QR expires.

In Bruno, select Component 4 Local, fill backofficeToken, prosumerToken, reservationId and transfer IDs. Request 14 obtains a QR; copy payload to qrPayload. Request 15 verifies with only payload. Then run Start, Record Progress and Complete. Do not run the entire folder unattended: cancel/fail and rejection examples need separate test reservations. expectedEnergyKWh in the environment is the final meter reading, not a value supplied to transfer creation.

Check invalid/expired QR (400), QR for another owner's reservation (403), unapproved/stale reservation or repeated completion (409), inactive/missing reservation owner, and inactive station/slot. Start before QR verification must fail. Repeated or concurrent creation must leave only one transfer.

Local validation: dotnet run --project tests/SolarGridX.TransferChecks
Database integration: append -- --integration. The integration runner creates and drops only its uniquely named disposable database using the configured replica-set MongoDB connection. It tests one active prosumer, server-derived station/slot identity, QR verification, concurrency, completion and capacity accounting.

After source updates, rebuild/restart the API, restart Vite and rerun the Android app. Keep the web and Android API address consistent. Keep ASP.NET Data Protection keys persistent for IIS; restarting with new keys invalidates earlier QR codes.

## Assigned stations and completed history

Backoffice opens Station Management and selects an active Grid Operator for each station. A station has one operator; an operator may manage several stations. Existing stations are unassigned until Backoffice selects an operator. Grid Operators can only read and act on their assigned stations' bookings, slots and transfers. Backoffice keeps system-wide access, and Prosumers keep their own bookings and read-only transfer history.

Open Dashboard on the web or Home in the operator mobile app. Select a station to filter its booking counts, active transfers, completed transfer count, completed kWh, upcoming active slots and available energy. Use Refresh to reload server totals. Available energy excludes expired slots and inactive stations.

After Complete, Transfers opens the Completed view automatically. Prosumers can select a completed booking and read delivered kWh, completion time and transfer history. Dashboard/Home also list recent completed transfers. Older completed records recover station and prosumer identity from their linked reservation without a database migration.

Bruno Energy Transfers requests 25–28 cover completed bookings, own completed transfers, station dashboard and foreign-station rejection. Microgrid requests 30–31 list active operators and assign a station. Set operatorNIC, gridOperatorToken and foreignStationId. Assignment requires a Backoffice token. Try a foreign station ID with an operator token: direct access and mutations must be forbidden, and list endpoints must not return unrelated rows.
