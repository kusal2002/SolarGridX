# Energy transfers: Prosumer ↔ Microgrid Station

One approved reservation identifies the prosumer, station, slot and reserved kWh. There is no second prosumer or seller selection. The physical direction may be charging or drop-off; the current reservation schema does not record that direction.

## Verify and deliver

1. The reservation owner requests GET /api/Reservation/{id}/qr. The protected QR expires in 15 minutes.
2. A Grid Operator or Backoffice user sends POST /api/energy-transfers/verify with { "payload": "SCANNED_QR" }.
3. The server checks QR validity, reservation version and Approved status, the active reservation owner, and the active station/slot. It creates or verifies one Pending transfer. The server derives prosumerNIC, stationId, slotId and expectedEnergyKWh; clients cannot override them.
4. PATCH /api/energy-transfers/{id}/start starts delivery and sets the reservation to InProgress.
5. PATCH /api/energy-transfers/{id}/progress records cumulative transferredEnergyKWh. PATCH /api/energy-transfers/{id}/complete requires the full expected energy and atomically sets the reservation to Completed.

Cancellation and failure use PATCH /{id}/cancel or /{id}/fail with a reason. Only undelivered energy is restored to the slot. Actor NICs come from authenticated claims; verification, readings and completion remain audited.

POST /api/energy-transfers accepts only { "reservationId": "24_CHARACTER_OBJECT_ID" } for manual Pending creation. It does not replace QR verification before Start.

GET /api/energy-transfers supports status, reservationId, prosumerNIC, stationId, page and pageSize. Detail and history use GET /{id} and GET /{id}/history. Responses include id, reservationId, prosumerNIC, stationId, slotId, expectedEnergyKWh, transferredEnergyKWh, status, verifiedBy, verifiedAt, timestamps and history.

Invalid requests return 400, missing login 401, forbidden role 403, missing records 404, and stale QR/state or duplicate transfers 409. A unique reservation index and MongoDB transactions prevent duplicate delivery and partial completion. MongoDB must support transactions (replica set or Atlas).

Older seller/buyer documents are readable. Existing linked Pending transfers acquire station–prosumer fields when reverified with a fresh QR; changing an active linked transfer also refreshes those fields. Unlinked legacy transfers still require review. No database records are automatically deleted.

See [testing steps](component-4-checklist.md).
