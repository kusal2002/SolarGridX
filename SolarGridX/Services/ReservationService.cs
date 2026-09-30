using MongoDB.Driver;
using SolarGridX.DTOs.Reservations;
using SolarGridX.Models;

namespace SolarGridX.Services;

public class ReservationService
{
    private readonly IMongoDatabase _database;
    private readonly IMongoCollection<EnergyReservation> _reservations;
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<SolarStation> _stations;
    private readonly IMongoCollection<User> _users;

    public ReservationService(IMongoDatabase database)
    {
        _database = database;
        _reservations = database.GetCollection<EnergyReservation>("EnergyReservations");
        _slots = database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
        _stations = database.GetCollection<SolarStation>("SolarStationInfo");
        _users = database.GetCollection<User>("Users");
    }

    // 1. Get All Reservations (Admin / Operator)
    public async Task<List<EnergyReservation>> GetAllAsync()
    {
        return await _reservations.Find(_ => true).ToListAsync();
    }

    // 2. Get By ID
    public async Task<EnergyReservation?> GetByIdAsync(string id)
    {
        return await _reservations.Find(r => r.Id == id).FirstOrDefaultAsync();
    }

    // 3. Get Reservations by Prosumer NIC
    public async Task<List<EnergyReservation>> GetByProsumerAsync(string prosumerNIC)
    {
        return await _reservations
            .Find(r => r.ProsumerNIC == prosumerNIC)
            .SortByDescending(r => r.CreatedAt)
            .ToListAsync();
    }

    // 4. Create Reservation
    public async Task<EnergyReservation> CreateAsync(CreateReservationRequest request)
    {
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => CreateCoreAsync(s, request));
    }

    private async Task<EnergyReservation> CreateCoreAsync(IClientSessionHandle session, CreateReservationRequest request)
    {
        // A. Validate user exists
        var user = await _users.Find(session, u => u.NIC == request.ProsumerNIC).FirstOrDefaultAsync();
        if (user == null)
            throw new KeyNotFoundException($"Prosumer with NIC '{request.ProsumerNIC}' not found.");
        if (user.Role != "Prosumer" || user.AccountStatus != AccountStatus.Active)
            throw new InvalidOperationException("Reservations require an active Prosumer account.");

        // B. Validate slot exists and is active
        var slot = await _slots.Find(session, s => s.Id == request.SlotId && s.IsActive).FirstOrDefaultAsync();
        if (slot == null)
            throw new KeyNotFoundException("The requested energy slot was not found or is inactive.");

        // C. Enforce 7-Day booking window rule
        var today = DateTime.UtcNow.Date;
        var slotDate = slot.SlotDate.Date;
        var dayDifference = (slotDate - today).TotalDays;

        if (dayDifference < 0)
            throw new InvalidOperationException("Cannot book a slot in the past.");
        if (dayDifference > 7)
            throw new InvalidOperationException("Reservations can only be made up to 7 days in advance.");

        // D. Check available capacity
        if (request.RequestedEnergyKwh > slot.AvailableEnergyKwh)
        {
            throw new InvalidOperationException(
                $"Requested energy ({request.RequestedEnergyKwh} kWh) exceeds available capacity ({slot.AvailableEnergyKwh} kWh).");
        }

        // E. Check for existing active reservation by this user on this slot
        var existingActive = await _reservations.Find(session, r =>
            r.ProsumerNIC == request.ProsumerNIC &&
            r.SlotId == request.SlotId &&
            (r.Status == "Pending" || r.Status == "Approved" || r.Status == "InProgress")
        ).FirstOrDefaultAsync();

        if (existingActive != null)
            throw new InvalidOperationException("You already have an active reservation for this slot.");

        // F. Deduct slot available energy
        var slotUpdate = Builders<EnergyBookingSlot>.Update
            .Set(s => s.AvailableEnergyKwh, slot.AvailableEnergyKwh - request.RequestedEnergyKwh)
            .Set(s => s.UpdatedAt, DateTime.UtcNow);
        await _slots.UpdateOneAsync(session, s => s.Id == slot.Id, slotUpdate);

        // G. Create reservation document
        var reservation = new EnergyReservation
        {
            ProsumerNIC = request.ProsumerNIC,
            StationId = slot.StationId,
            SlotId = slot.Id,
            ReservationDate = slot.SlotDate,
            StartTime = slot.StartTime,
            EndTime = slot.EndTime,
            RequestedEnergyKwh = request.RequestedEnergyKwh,
            Status = "Pending",
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        await _reservations.InsertOneAsync(session, reservation);
        return reservation;
    }

    // 5. Cancel Reservation (12-hour rule)
    public async Task<EnergyReservation> CancelAsync(string id, string? reason)
    {
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => CancelCoreAsync(s, id, reason));
    }

    private async Task<EnergyReservation> CancelCoreAsync(IClientSessionHandle session, string id, string? reason)
    {
        var reservation = await _reservations.Find(session, r => r.Id == id).FirstOrDefaultAsync();
        if (reservation == null)
            throw new KeyNotFoundException("Reservation not found.");
        await EnsureNoTransferAsync(session, reservation);

        if (reservation.Status == "Cancelled" || reservation.Status == "Completed")
            throw new InvalidOperationException($"Cannot cancel a reservation that is already {reservation.Status}.");

        // 12-Hour Restriction Check
        var slotStart = reservation.ReservationDate.Date.Add(reservation.StartTime);
        if ((slotStart - DateTime.UtcNow).TotalHours < 12)
        {
            throw new InvalidOperationException("Cancellations must be made at least 12 hours before the scheduled slot time.");
        }

        // Restore slot energy
        var slotUpdate = Builders<EnergyBookingSlot>.Update
            .Inc(s => s.AvailableEnergyKwh, reservation.RequestedEnergyKwh)
            .Set(s => s.UpdatedAt, DateTime.UtcNow);
        await _slots.UpdateOneAsync(session, s => s.Id == reservation.SlotId, slotUpdate);

        // Update reservation status
        reservation.Status = "Cancelled";
        reservation.CancellationReason = reason ?? "Cancelled by user";
        reservation.UpdatedAt = DateTime.UtcNow;

        await _reservations.ReplaceOneAsync(session, r => r.Id == id, reservation);
        return reservation;
    }

    // 6. Modify Reservation (12-hour rule)
    public async Task<EnergyReservation> UpdateAsync(string id, UpdateReservationRequest request)
    {
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => UpdateCoreAsync(s, id, request));
    }

    private async Task<EnergyReservation> UpdateCoreAsync(IClientSessionHandle session, string id, UpdateReservationRequest request)
    {
        var reservation = await _reservations.Find(session, r => r.Id == id).FirstOrDefaultAsync();
        if (reservation == null)
            throw new KeyNotFoundException("Reservation not found.");
        await EnsureNoTransferAsync(session, reservation);

        if (reservation.Status != "Pending" && reservation.Status != "Approved")
            throw new InvalidOperationException($"Cannot modify a reservation with status '{reservation.Status}'.");

        // 12-Hour Restriction Check on current slot
        var currentSlotStart = reservation.ReservationDate.Date.Add(reservation.StartTime);
        if ((currentSlotStart - DateTime.UtcNow).TotalHours < 12)
        {
            throw new InvalidOperationException("Modifications must be made at least 12 hours before the scheduled slot time.");
        }

        var targetKwh = request.RequestedEnergyKwh ?? reservation.RequestedEnergyKwh;

        // Case A: Switching to a different slot
        if (!string.IsNullOrEmpty(request.NewSlotId) && request.NewSlotId != reservation.SlotId)
        {
            var newSlot = await _slots.Find(session, s => s.Id == request.NewSlotId && s.IsActive).FirstOrDefaultAsync();
            if (newSlot == null)
                throw new KeyNotFoundException("The new energy slot was not found or is inactive.");

            // 7-day window rule for new slot
            var dayDifference = (newSlot.SlotDate.Date - DateTime.UtcNow.Date).TotalDays;
            if (dayDifference < 0 || dayDifference > 7)
                throw new InvalidOperationException("New slot must be within the 7-day booking window.");

            // Check capacity in new slot
            if (targetKwh > newSlot.AvailableEnergyKwh)
                throw new InvalidOperationException($"Requested energy ({targetKwh} kWh) exceeds available capacity in the new slot ({newSlot.AvailableEnergyKwh} kWh).");

            // Restore energy to old slot
            await _slots.UpdateOneAsync(session,
                s => s.Id == reservation.SlotId,
                Builders<EnergyBookingSlot>.Update.Inc(s => s.AvailableEnergyKwh, reservation.RequestedEnergyKwh).Set(s => s.UpdatedAt, DateTime.UtcNow)
            );

            // Deduct energy from new slot
            await _slots.UpdateOneAsync(session,
                s => s.Id == newSlot.Id,
                Builders<EnergyBookingSlot>.Update.Inc(s => s.AvailableEnergyKwh, -targetKwh).Set(s => s.UpdatedAt, DateTime.UtcNow)
            );

            // Update reservation references
            reservation.StationId = newSlot.StationId;
            reservation.SlotId = newSlot.Id;
            reservation.ReservationDate = newSlot.SlotDate;
            reservation.StartTime = newSlot.StartTime;
            reservation.EndTime = newSlot.EndTime;
            reservation.RequestedEnergyKwh = targetKwh;
        }
        // Case B: Same slot, updating requested kWh
        else if (request.RequestedEnergyKwh.HasValue && request.RequestedEnergyKwh.Value != reservation.RequestedEnergyKwh)
        {
            var currentSlot = await _slots.Find(session, s => s.Id == reservation.SlotId).FirstOrDefaultAsync();
            if (currentSlot == null)
                throw new KeyNotFoundException("Associated slot not found.");

            var difference = targetKwh - reservation.RequestedEnergyKwh;
            if (difference > 0 && difference > currentSlot.AvailableEnergyKwh)
            {
                throw new InvalidOperationException($"Additional requested energy ({difference} kWh) exceeds available slot capacity ({currentSlot.AvailableEnergyKwh} kWh).");
            }

            await _slots.UpdateOneAsync(session,
                s => s.Id == reservation.SlotId,
                Builders<EnergyBookingSlot>.Update.Inc(s => s.AvailableEnergyKwh, -difference).Set(s => s.UpdatedAt, DateTime.UtcNow)
            );

            reservation.RequestedEnergyKwh = targetKwh;
        }

        reservation.UpdatedAt = DateTime.UtcNow;
        await _reservations.ReplaceOneAsync(session, r => r.Id == id, reservation);
        return reservation;
    }

    // 7. Approve a pending reservation; transfers own the delivery lifecycle.
    public async Task<EnergyReservation> UpdateStatusAsync(string id, string newStatus)
    {
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => UpdateStatusCoreAsync(s, id, newStatus));
    }

    private async Task<EnergyReservation> UpdateStatusCoreAsync(IClientSessionHandle session, string id, string newStatus)
    {
        var reservation = await _reservations.Find(session, r => r.Id == id).FirstOrDefaultAsync();
        if (reservation == null)
            throw new KeyNotFoundException("Reservation not found.");
        await EnsureNoTransferAsync(session, reservation);

        if (newStatus != "Approved" || reservation.Status != "Pending")
            throw new InvalidOperationException("Only Pending -> Approved is allowed here. Use reservation cancellation or the transfer lifecycle for other transitions.");
        reservation.Status = newStatus;
        reservation.UpdatedAt = DateTime.UtcNow;

        await _reservations.ReplaceOneAsync(session, r => r.Id == id, reservation);
        return reservation;
    }
    private async Task EnsureNoTransferAsync(IClientSessionHandle session, EnergyReservation reservation)
    {
        // Include old transfers that predate the reservation link.
        var linked = await _database.GetCollection<EnergyTransfer>("EnergyTransfers")
            .Find(session, t => t.ReservationId == reservation.Id,
                new FindOptions { Collation = new Collation("en", strength: CollationStrength.Secondary) })
            .AnyAsync();
        if (reservation.TransferId != null || linked)
            throw new InvalidOperationException("This reservation has an energy transfer. Use the transfer lifecycle to change it.");
    }

}
