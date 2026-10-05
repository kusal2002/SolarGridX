// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: EnergyTransferService.cs
// Description: Service class handling energy transfer transactions, QR verification, meter progress, and completions.
// ============================================================================

using MongoDB.Bson;
using MongoDB.Driver;
using SolarGridX.DTOs;
using SolarGridX.Models;

namespace SolarGridX.Services;

public class EnergyTransferService
{
    private readonly IMongoDatabase _database;
    private readonly IMongoCollection<EnergyTransfer> _transfers;
    private readonly IMongoCollection<EnergyReservation> _reservations;
    private readonly IMongoCollection<User> _users;
    private readonly IMongoCollection<EnergyBookingSlot> _slots;

    public EnergyTransferService(IMongoDatabase database)
    {
        // Initialize Mongo database and collections for transfers, reservations, users, and slots
        _database = database;
        _transfers = database.GetCollection<EnergyTransfer>("EnergyTransfers");
        _reservations = database.GetCollection<EnergyReservation>("EnergyReservations");
        _users = database.GetCollection<User>("Users");
        _slots = database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
    }

    public async Task EnsureIndexesAsync()
    {
        // Set up unique indexes for transfer reservation lookups and creation timestamps
        // Case-insensitive comparison also covers legacy uppercase ObjectId strings.
        // Existing duplicates deliberately fail startup; never silently delete business records.
        await _transfers.Indexes.CreateOneAsync(new CreateIndexModel<EnergyTransfer>(
            Builders<EnergyTransfer>.IndexKeys.Ascending(t => t.ReservationId),
            new CreateIndexOptions { Unique = true, Name = "ux_transfer_reservation",
                Collation = new Collation("en", strength: CollationStrength.Secondary) }));
        await _transfers.Indexes.CreateOneAsync(new CreateIndexModel<EnergyTransfer>(
            Builders<EnergyTransfer>.IndexKeys.Descending(t => t.CreatedAt).Descending(t => t.id),
            new CreateIndexOptions { Name = "ix_transfer_created" }));
    }

    public async Task<List<EnergyTransfer>> GetAllAsync(EnergyTransferQuery query, CancellationToken ct = default, IEnumerable<string>? allowedReservations = null)
    {
        // Query energy transfers with filters, sorting, and backfill prosumer/station IDs for legacy records
        var f = Builders<EnergyTransfer>.Filter;
        var filter = f.Empty;
        if (allowedReservations != null) filter &= f.In(t => t.ReservationId, allowedReservations);
        if (query.Status != null) filter &= f.Eq(t => t.Status, query.Status);
        if (query.ReservationId != null) filter &= f.Eq(t => t.ReservationId, NormalizeId(query.ReservationId));
        if (query.ProsumerNIC != null) filter &= f.Eq(t => t.ProsumerNIC, query.ProsumerNIC);
        if (query.StationId != null) filter &= f.Eq(t => t.StationId, NormalizeId(query.StationId));
        var list = await _transfers.Find(filter, new FindOptions { Collation = new Collation("en", strength: CollationStrength.Secondary) })
            .SortByDescending(t => t.CreatedAt).ThenByDescending(t => t.id)
            .Skip((query.Page - 1) * query.PageSize).Limit(query.PageSize).ToListAsync(ct);
        foreach (var transfer in list) await HydrateLegacyIdentityAsync(transfer, ct);
        return list;
    }

    public async Task<EnergyTransfer?> GetByIdAsync(string id, CancellationToken ct = default)
    {
        // Fetch an energy transfer by ID and populate any legacy reservation data
        id = NormalizeId(id);
        var transfer = await _transfers.Find(t => t.id == id).FirstOrDefaultAsync(ct);
        if (transfer != null) await HydrateLegacyIdentityAsync(transfer, ct);
        return transfer;
    }

    public Task<EnergyReservation?> GetReservationByIdAsync(string reservationId)
    {
        // Look up a reservation linked to a transfer by its ID
        reservationId = NormalizeId(reservationId);
        return _reservations.Find(r => r.Id == reservationId).FirstOrDefaultAsync()!;
    }

    public async Task<EnergyTransfer> CreateAsync(CreateEnergyTransferRequest request, string actor, CancellationToken ct = default,
        ReservationQrService.Ticket? ticket = null)
    {
        // Create or verify an energy transfer record from an approved reservation within a transaction
        var reservationId = NormalizeId(request.ReservationId);

        try
        {
            using var session = await _database.Client.StartSessionAsync(cancellationToken: ct);
            return await session.WithTransactionAsync(async (s, token) =>
            {
                var reservation = await _reservations.Find(s, r => r.Id == reservationId).FirstOrDefaultAsync(token)
                    ?? throw new TransferException(404, "Reservation not found.");
                if (reservation.Status != "Approved")
                    throw new TransferException(409, "Only approved reservations can create a transfer.");
                if (ticket != null && (ticket.ReservationId != reservationId || ticket.Version != reservation.UpdatedAt.Ticks || ticket.ExpiresAt <= DateTime.UtcNow))
                    throw new TransferException(409, "QR is stale. Ask the prosumer to refresh it.");
                if (reservation.TransferId != null && ticket == null)
                    throw new TransferException(409, "A transfer already exists for this reservation.");
                if (!double.IsFinite(reservation.RequestedEnergyKwh) || reservation.RequestedEnergyKwh < 0.001 ||
                    reservation.RequestedEnergyKwh > 1000000)
                    throw new TransferException(400, "Reservation has invalid requested energy.");
                    var user = await _users.Find(s, u => u.NIC == reservation.ProsumerNIC).FirstOrDefaultAsync(token)
                        ?? throw new TransferException(404, "Reservation prosumer account not found.");
                    if (user.Role != "Prosumer" || user.AccountStatus != AccountStatus.Active)
                        throw new TransferException(409, "Reservation owner must be an active Prosumer.");
                await RequireActiveSlotAsync(s, reservation, token);
                var now = DateTime.UtcNow;
                if (reservation.TransferId != null)
                {
                    var existing = await _transfers.Find(s, t => t.id == reservation.TransferId).FirstOrDefaultAsync(token);
                    if (existing == null || existing.Status != "Pending")
                        throw new TransferException(409, "Transfer is missing or already started.");
                    SetReservationIdentity(existing, reservation);
                    MarkVerified(existing, actor, now);
                    await _transfers.ReplaceOneAsync(s, t => t.id == existing.id, existing, cancellationToken: token);
                    return existing;
                }
                var transfer = new EnergyTransfer
                {
                    id = ObjectId.GenerateNewId().ToString(), ReservationId = reservationId,
                    ProsumerNIC = reservation.ProsumerNIC, StationId = reservation.StationId, SlotId = reservation.SlotId,
                    ExpectedEnergyKWh = (decimal)reservation.RequestedEnergyKwh, CreatedAt = now, UpdatedAt = now,
                    History = [new EnergyTransferEvent { Action = "create", Status = "Pending", ActorNIC = actor, At = now }]
                };
                if (ticket != null) MarkVerified(transfer, actor, now);
                reservation.TransferId = transfer.id;
                reservation.UpdatedAt = now;
                await _reservations.ReplaceOneAsync(s, r => r.Id == reservationId, reservation, cancellationToken: token);
                await _transfers.InsertOneAsync(s, transfer, cancellationToken: token);
                return transfer;
            }, cancellationToken: ct);
        }
        catch (MongoWriteException ex) when (ex.WriteError.Category == ServerErrorCategory.DuplicateKey)
        {
            throw new TransferException(409, "A transfer already exists for this reservation.");
        }
    }

    public async Task<EnergyTransfer> ChangeAsync(string id, string action, decimal? energy, string? reason, string actor, CancellationToken ct = default)
    {
        // Execute transfer status change, update meter readings, and adjust slot capacity on cancellation/failure
        id = NormalizeId(id);
        using var session = await _database.Client.StartSessionAsync(cancellationToken: ct);
        return await session.WithTransactionAsync(async (s, token) =>
        {
            var transfer = await _transfers.Find(s, t => t.id == id).FirstOrDefaultAsync(token)
                ?? throw new TransferException(404, "Energy transfer not found.");
            var reservationId = NormalizeId(transfer.ReservationId);
            var reservation = await _reservations.Find(s, r => r.Id == reservationId).FirstOrDefaultAsync(token)
                ?? throw new TransferException(409, "Associated reservation is missing.");
            var expectedStatus = transfer.Status == "Pending" ? "Approved" : "InProgress";
            SetReservationIdentity(transfer, reservation);
            // Legacy transfers need explicit review before being attached to a reservation.
            if (reservation.TransferId != transfer.id || reservation.Status != expectedStatus)
                throw new TransferException(409, "Reservation is not linked to this active transfer; review legacy data if applicable.");
            if (action is "start" or "complete" && transfer.VerifiedAt == null)
                throw new TransferException(409, "Scan and verify the reservation QR before starting or completing.");
            if (action == "start") await RequireActiveSlotAsync(s, reservation, token);
            EnergyTransferRules.Apply(transfer, action, energy, reason, actor, DateTime.UtcNow);
            reservation.Status = transfer.Status switch
            {
                "InProgress" => "InProgress", "Completed" => "Completed",
                "Cancelled" or "Failed" => "Cancelled", _ => reservation.Status
            };
            reservation.UpdatedAt = transfer.UpdatedAt;
            if (action is "cancel" or "fail")
            {
                reservation.CancellationReason = transfer.Reason;
                // Capacity was already deducted when the reservation was booked.
                // Restore only undelivered energy, atomically with the terminal state.
                var unused = transfer.ExpectedEnergyKWh - transfer.TransferredEnergyKWh;
                var result = await _slots.UpdateOneAsync(s, slot => slot.Id == reservation.SlotId,
                    Builders<EnergyBookingSlot>.Update.Inc(slot => slot.AvailableEnergyKwh, (double)unused)
                        .Set(slot => slot.UpdatedAt, transfer.UpdatedAt), cancellationToken: token);
                if (result.MatchedCount != 1) throw new TransferException(409, "Associated slot is missing.");
            }
            await _reservations.ReplaceOneAsync(s, r => r.Id == reservation.Id, reservation, cancellationToken: token);
            await _transfers.ReplaceOneAsync(s, t => t.id == id, transfer, cancellationToken: token);
            return transfer;
        }, cancellationToken: ct);
    }

    private async Task RequireActiveSlotAsync(IClientSessionHandle session, EnergyReservation reservation, CancellationToken ct)
    {
        // Check that both the station and slot exist and are currently active
        var slot = await _slots.Find(session, s => s.Id == reservation.SlotId && s.StationId == reservation.StationId && s.IsActive)
            .FirstOrDefaultAsync(ct);
        var station = await _database.GetCollection<SolarStation>("SolarStationInfo")
            .Find(session, s => s.Id == reservation.StationId && s.IsActive).FirstOrDefaultAsync(ct);
        if (slot == null || station == null)
            throw new TransferException(409, "Reservation station and slot must exist and be active.");
    }

    private static string NormalizeId(string id)
    {
        // Parse and validate string as a valid 24-character hexadecimal MongoDB ObjectId
        return ObjectId.TryParse(id, out var parsed)
            ? parsed.ToString() : throw new TransferException(400, "ID must be a 24-character MongoDB ObjectId.");
    }

    private static void MarkVerified(EnergyTransfer transfer, string actor, DateTime now)
    {
        // Record the operator NIC and verification timestamp on the transfer entity
        transfer.VerifiedBy = actor;
        transfer.VerifiedAt = now;
        transfer.UpdatedAt = now;
        transfer.History.Add(new EnergyTransferEvent { Action = "verify", Status = transfer.Status, ActorNIC = actor, At = now });
    }

    private static void SetReservationIdentity(EnergyTransfer transfer, EnergyReservation reservation)
    {
        // Map reservation details (prosumer, station, slot) onto the transfer model
        transfer.ProsumerNIC = reservation.ProsumerNIC;
        transfer.StationId = reservation.StationId;
        transfer.SlotId = reservation.SlotId;
    }

    private async Task HydrateLegacyIdentityAsync(EnergyTransfer transfer, CancellationToken ct)
    {
        // Backfill station, slot, and prosumer info from the reservation for legacy transfer documents
        if (!string.IsNullOrEmpty(transfer.ProsumerNIC) && !string.IsNullOrEmpty(transfer.StationId)) return;
        if (!ObjectId.TryParse(transfer.ReservationId, out var id)) return;
        var reservation = await _reservations.Find(r => r.Id == id.ToString()).FirstOrDefaultAsync(ct);
        if (reservation != null) SetReservationIdentity(transfer, reservation);
    }
}
