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
        _database = database;
        _transfers = database.GetCollection<EnergyTransfer>("EnergyTransfers");
        _reservations = database.GetCollection<EnergyReservation>("EnergyReservations");
        _users = database.GetCollection<User>("Users");
        _slots = database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
    }

    public async Task EnsureIndexesAsync()
    {
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

    public Task<List<EnergyTransfer>> GetAllAsync(EnergyTransferQuery query, CancellationToken ct = default)
    {
        var f = Builders<EnergyTransfer>.Filter;
        var filter = f.Empty;
        if (query.Status != null) filter &= f.Eq(t => t.Status, query.Status);
        if (query.ReservationId != null) filter &= f.Eq(t => t.ReservationId, NormalizeId(query.ReservationId));
        if (query.SellerId != null) filter &= f.Eq(t => t.SellerId, query.SellerId);
        if (query.BuyerId != null) filter &= f.Eq(t => t.BuyerId, query.BuyerId);
        return _transfers.Find(filter, new FindOptions { Collation = new Collation("en", strength: CollationStrength.Secondary) })
            .SortByDescending(t => t.CreatedAt).ThenByDescending(t => t.id)
            .Skip((query.Page - 1) * query.PageSize).Limit(query.PageSize).ToListAsync(ct);
    }

    public Task<EnergyTransfer?> GetByIdAsync(string id, CancellationToken ct = default)
    {
        id = NormalizeId(id);
        return _transfers.Find(t => t.id == id).FirstOrDefaultAsync(ct)!;
    }

    public Task<EnergyReservation?> GetReservationByIdAsync(string reservationId)
    {
        reservationId = NormalizeId(reservationId);
        return _reservations.Find(r => r.Id == reservationId).FirstOrDefaultAsync()!;
    }

    public async Task<EnergyTransfer> CreateAsync(CreateEnergyTransferRequest request, string actor, CancellationToken ct = default)
    {
        var reservationId = NormalizeId(request.ReservationId);
        if (string.IsNullOrWhiteSpace(request.SellerId) || string.IsNullOrWhiteSpace(request.BuyerId) ||
            request.SellerId == request.BuyerId || request.ExpectedEnergyKWh < 0.001m || request.ExpectedEnergyKWh > 1000000m)
            throw new TransferException(400, "Distinct seller and buyer NICs and valid expected energy are required.");

        try
        {
            using var session = await _database.Client.StartSessionAsync(cancellationToken: ct);
            return await session.WithTransactionAsync(async (s, token) =>
            {
                var reservation = await _reservations.Find(s, r => r.Id == reservationId).FirstOrDefaultAsync(token)
                    ?? throw new TransferException(404, "Reservation not found.");
                if (reservation.Status != "Approved")
                    throw new TransferException(409, "Only approved reservations can create a transfer.");
                if (reservation.TransferId != null)
                    throw new TransferException(409, "A transfer already exists for this reservation.");
                if (reservation.ProsumerNIC != request.BuyerId)
                    throw new TransferException(400, "Buyer NIC must match the reservation's prosumer NIC.");
                if (!double.IsFinite(reservation.RequestedEnergyKwh) || reservation.RequestedEnergyKwh < 0.001 ||
                    reservation.RequestedEnergyKwh > 1000000 || request.ExpectedEnergyKWh != (decimal)reservation.RequestedEnergyKwh)
                    throw new TransferException(400, "Expected energy must equal the reservation's requested energy.");
                foreach (var nic in new[] { request.SellerId, request.BuyerId })
                {
                    var user = await _users.Find(s, u => u.NIC == nic).FirstOrDefaultAsync(token)
                        ?? throw new TransferException(404, "Seller or buyer account not found.");
                    if (user.Role != "Prosumer" || user.AccountStatus != AccountStatus.Active)
                        throw new TransferException(409, "Seller and buyer must be active Prosumers.");
                }
                await RequireActiveSlotAsync(s, reservation, token);
                var now = DateTime.UtcNow;
                var transfer = new EnergyTransfer
                {
                    id = ObjectId.GenerateNewId().ToString(), ReservationId = reservationId,
                    SellerId = request.SellerId, BuyerId = reservation.ProsumerNIC,
                    ExpectedEnergyKWh = request.ExpectedEnergyKWh, CreatedAt = now, UpdatedAt = now,
                    History = [new EnergyTransferEvent { Action = "create", Status = "Pending", ActorNIC = actor, At = now }]
                };
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
            // Legacy transfers need explicit review before being attached to a reservation.
            if (reservation.TransferId != transfer.id || reservation.Status != expectedStatus)
                throw new TransferException(409, "Reservation is not linked to this active transfer; review legacy data if applicable.");
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
        var slot = await _slots.Find(session, s => s.Id == reservation.SlotId && s.StationId == reservation.StationId && s.IsActive)
            .FirstOrDefaultAsync(ct);
        var station = await _database.GetCollection<SolarStation>("SolarStationInfo")
            .Find(session, s => s.Id == reservation.StationId && s.IsActive).FirstOrDefaultAsync(ct);
        if (slot == null || station == null)
            throw new TransferException(409, "Reservation station and slot must exist and be active.");
    }

    private static string NormalizeId(string id) => ObjectId.TryParse(id, out var parsed)
        ? parsed.ToString() : throw new TransferException(400, "ID must be a 24-character MongoDB ObjectId.");
}
