# Component 4 requests

Select the **Component 4 Local** environment (`http://localhost:5084`) or add its variables to your existing environment. Fill `backofficeToken` with a Backoffice or Grid Operator token and `prosumerToken` with the reservation owner's token. Do not commit real tokens.

Use one active Prosumer with an approved station/slot reservation and one Grid Operator. Set `reservationId` and `expectedEnergyKWh` from the reservation; no seller account is needed. Verify sends only the QR payload, and the server derives the prosumer, station, slot and expected energy.

For the simplest flow:

1. Run **14 Get approved reservation QR**. Copy `payload` into `qrPayload`.
2. Run **15 Verify QR**. Copy the returned `id` into `transferId`. Verification creates a pending transfer if one does not exist.
3. Run **05 Start**, then **06 Record Progress** and **07 Complete**. Set `progressEnergyKWh` to a partial cumulative reading; completion sends `expectedEnergyKWh`.
4. Run **08 History**, **16 Dashboard**, **17 Booking history**, **18 Current bookings**, and **19 Pending bookings**.
5. Use **20 Search** with `bookingSearch` (NIC, reservation ID or station ID), optional `bookingStatus`, and optional `stationId`. Leave optional values blank for all records.
6. **21 Prosumer history** must only return that token owner's bookings. Requests **22–24** check invalid QR, forbidden Prosumer verification and missing-token rejection.

If testing **01 Create** and **02 Duplicate**, run them before getting a fresh QR and verifying; Start requires verification. For cancel/fail tests use separate bookings and set `cancelTransferId` or `failTransferId`. Do not run the entire folder in sequence: several requests intentionally require different fixtures or fail.

The **Microgrid & Slot Management** folder also includes station/slot creation, list, detail, update, deactivation and reactivation requests. Set `stationId`, `slotId` and a future `slotDate`; use a Backoffice token for station mutations. Review sample bodies to match your test station before sending them. Existing requests now use `baseUrl` and bearer tokens; **Create slots** sends POST, and station creation includes operating hours.

For the web pages, start Vite from this checkout's `Client` folder. Restart it after updating the auth entry point and hard refresh the browser (`Ctrl+Shift+R`). Stations and Slots are under **Station Management** in the sidebar; Reservations and Transfers have their own links. The header only contains breadcrumbs, the sidebar toggle and the theme toggle.
