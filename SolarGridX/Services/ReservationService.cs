// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: ReservationService.cs
// Description: Service class handling reservation business logic, 7-day booking windows, and 12-hour rules.
// ============================================================================

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
        // Initialize MongoDB collections for reservations, slots, stations, and users
        _database = database;
        _reservations = database.GetCollection<EnergyReservation>("EnergyReservations");
        _slots = database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
        _stations = database.GetCollection<SolarStation>("SolarStationInfo");
        _users = database.GetCollection<User>("Users");
    }

    private DateTime GetSriLankaTime()
    {
        // Get current date and time converted to Sri Lanka Standard Time
        try
        {
            var tz = TimeZoneInfo.FindSystemTimeZoneById("Asia/Colombo");
            return TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);
        }
        catch (TimeZoneNotFoundException)
        {
            // Fallback for Windows environments
            var tz = TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time");
            return TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);
        }
    }

    public async Task PopulateDisplayNamesAsync(List<EnergyReservation> list)
    {
        // Look up prosumer and station names to display on reservation cards and tables
        var users = await _users.Find(Builders<User>.Filter.In(u => u.NIC, list.Select(r => r.ProsumerNIC).Distinct()))
            .Project(u => new { u.NIC, u.Name }).ToListAsync();
        var stations = await _stations.Find(Builders<SolarStation>.Filter.In(s => s.Id, list.Select(r => r.StationId).Distinct()))
            .Project(s => new { s.Id, s.StationName }).ToListAsync();
        var names = users.ToDictionary(u => u.NIC, u => u.Name);
        var stationNames = stations.ToDictionary(s => s.Id, s => s.StationName);
        foreach (var reservation in list)
        {
            reservation.ProsumerName = names.GetValueOrDefault(reservation.ProsumerNIC);
            reservation.StationName = stationNames.GetValueOrDefault(reservation.StationId);
        }
    }

    // 1. Get All Reservations (Admin / Operator)
    public async Task<List<EnergyReservation>> GetAllAsync()
    {
        // Retrieve all energy reservations from the database
        return await _reservations.Find(_ => true).ToListAsync();
    }

    // 2. Get By ID
    public async Task<EnergyReservation?> GetByIdAsync(string id)
    {
        // Find a specific energy reservation by its unique identifier
        return await _reservations.Find(r => r.Id == id).FirstOrDefaultAsync();
    }

    // 3. Get Reservations by Prosumer NIC
    public async Task<List<EnergyReservation>> GetByProsumerAsync(string prosumerNIC)
    {
        // Fetch all reservations belonging to a specific prosumer NIC ordered newest first
        return await _reservations
            .Find(r => r.ProsumerNIC == prosumerNIC)
            .SortByDescending(r => r.CreatedAt)
            .ToListAsync();
    }

    // 4. Create Reservation
    public async Task<EnergyReservation> CreateAsync(CreateReservationRequest request)
    {
        // Start a database transaction session to create a new reservation safely
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => CreateCoreAsync(s, request));
    }

    private async Task<EnergyReservation> CreateCoreAsync(IClientSessionHandle session, CreateReservationRequest request)
    {
        // Core reservation logic: validate user, slot, 7-day booking window, and deduct slot energy
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

        // C. Enforce 7-Day booking window rule and unexpired check
        var now = GetSriLankaTime();
        var today = now.Date;
        var slotDate = slot.SlotDate.Date;
        var dayDifference = (slotDate - today).TotalDays;

        if (dayDifference < 0)
            throw new InvalidOperationException("Cannot book a slot in the past.");
        if (dayDifference > 7)
            throw new InvalidOperationException("Reservations can only be made up to 7 days in advance.");

        var slotEnd = slotDate.Add(slot.EndTime);
        if (slotEnd <= now)
            throw new InvalidOperationException("Cannot book a slot that has already expired.");

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
        // Start transaction session to safely cancel a reservation and restore slot capacity
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => CancelCoreAsync(s, id, reason));
    }

    private async Task<EnergyReservation> CancelCoreAsync(IClientSessionHandle session, string id, string? reason)
    {
        // Core cancellation logic: enforce 12-hour rule before slot start, restore slot capacity, and cancel
        var reservation = await _reservations.Find(session, r => r.Id == id).FirstOrDefaultAsync();
        if (reservation == null)
            throw new KeyNotFoundException("Reservation not found.");
        await EnsureNoTransferAsync(session, reservation);

        if (reservation.Status == "Cancelled" || reservation.Status == "Completed")
            throw new InvalidOperationException($"Cannot cancel a reservation that is already {reservation.Status}.");

        // 12-Hour Restriction Check
        var slotStart = reservation.ReservationDate.Date.Add(reservation.StartTime);
        if ((slotStart - GetSriLankaTime()).TotalHours < 12)
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
        // Start transaction session to update an existing reservation
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => UpdateCoreAsync(s, id, request));
    }

    private async Task<EnergyReservation> UpdateCoreAsync(IClientSessionHandle session, string id, UpdateReservationRequest request)
    {
        // Core update logic: enforce 12-hour modification rule, handle slot switches, and balance capacities
        var reservation = await _reservations.Find(session, r => r.Id == id).FirstOrDefaultAsync();
        if (reservation == null)
            throw new KeyNotFoundException("Reservation not found.");
        await EnsureNoTransferAsync(session, reservation);

        if (reservation.Status != "Pending" && reservation.Status != "Approved")
            throw new InvalidOperationException($"Cannot modify a reservation with status '{reservation.Status}'.");

        // 12-Hour Restriction Check on current slot
        var currentSlotStart = reservation.ReservationDate.Date.Add(reservation.StartTime);
        if ((currentSlotStart - GetSriLankaTime()).TotalHours < 12)
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

            // 7-day window rule for new slot and expiration check
            var now = GetSriLankaTime();
            var dayDifference = (newSlot.SlotDate.Date - now.Date).TotalDays;
            if (dayDifference < 0 || dayDifference > 7)
                throw new InvalidOperationException("New slot must be within the 7-day booking window.");

            var newSlotEnd = newSlot.SlotDate.Date.Add(newSlot.EndTime);
            if (newSlotEnd <= now)
                throw new InvalidOperationException("Cannot switch to an expired slot.");

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
        // Start transaction session to approve a pending reservation
        using var session = await _database.Client.StartSessionAsync();
        return await session.WithTransactionAsync((s, ct) => UpdateStatusCoreAsync(s, id, newStatus));
    }

    private async Task<EnergyReservation> UpdateStatusCoreAsync(IClientSessionHandle session, string id, string newStatus)
    {
        // Approve a pending reservation after ensuring no transfer already exists
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
        // Verify no energy transfer is already linked so reservation edits do not conflict with delivery
        var linked = await _database.GetCollection<EnergyTransfer>("EnergyTransfers")
            .Find(session, t => t.ReservationId == reservation.Id,
                new FindOptions { Collation = new Collation("en", strength: CollationStrength.Secondary) })
            .AnyAsync();
        if (reservation.TransferId != null || linked)
            throw new InvalidOperationException("This reservation has an energy transfer. Use the transfer lifecycle to change it.");
    }

}
