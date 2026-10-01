using MongoDB.Driver;
using SolarGridX.DTOs.Slots;
using SolarGridX.Models;

namespace SolarGridX.Services;

public class SlotService
{
    private readonly IMongoCollection<EnergyBookingSlot> _slots;
    private readonly IMongoCollection<SolarStation> _stations;

    public SlotService(IMongoDatabase database)
    {
        _slots = database.GetCollection<EnergyBookingSlot>(
            "EnergyBookingSlots"
        );

        _stations = database.GetCollection<SolarStation>(
            "SolarStationInfo"
        );
    }

    // Get all active slots
    public async Task<List<EnergyBookingSlot>> GetAllAsync()
    {
        return await _slots
            .Find(slot => slot.IsActive == true)
            .ToListAsync();
    }

    // Get all slots including inactive
    public async Task<List<EnergyBookingSlot>> GetAllIncludingInactiveAsync()
    {
        return await _slots
            .Find(_ => true)
            .ToListAsync();
    }

    // Get slot by Id
    public async Task<EnergyBookingSlot?> GetByIdAsync(string id)
    {
        return await _slots
            .Find(slot =>
                slot.Id == id &&
                slot.IsActive == true)
            .FirstOrDefaultAsync();
    }

    // Get slots by station Id
    public async Task<List<EnergyBookingSlot>> GetByStationIdAsync(
        string stationId)
    {
        return await _slots
            .Find(slot =>
                slot.StationId == stationId &&
                slot.IsActive == true)
            .ToListAsync();
    }

    // Get all slots (including inactive) by station Id
    public async Task<List<EnergyBookingSlot>> GetByStationIdIncludingInactiveAsync(
        string stationId)
    {
        return await _slots
            .Find(slot => slot.StationId == stationId)
            .ToListAsync();
    }

    // Create slot
    public async Task<EnergyBookingSlot?> CreateAsync(
        CreateSlotRequest request)
    {
        var requestedDate = request.SlotDate.Date;
        var currentDate = DateTime.Now.Date;

        if (requestedDate < currentDate)
        {
            throw new ArgumentException("Slot date cannot be in the past.");
        }

        // Validate time range
        if (request.StartTime >= request.EndTime)
        {
            throw new ArgumentException(
                "Start time must be before end time."
            );
        }

        if (requestedDate == currentDate)
        {
            var currentTime = DateTime.Now.TimeOfDay;
            if (request.StartTime <= currentTime)
            {
                throw new ArgumentException("For today's date, the start time must be later than the current time.");
            }
        }

        // Validate energy capacity
        if (request.EnergyCapacityKwh <= 0)
        {
            throw new ArgumentException(
                "Energy capacity must be greater than zero."
            );
        }

        // Check station exists & active
        var station = await _stations
            .Find(item =>
                item.Id == request.StationId &&
                item.IsActive == true)
            .FirstOrDefaultAsync();

        if (station is null)
        {
            return null;
        }

        // Validate slot capacity does not exceed station total capacity
        if (request.EnergyCapacityKwh > station.TotalCapacityKwh)
        {
            throw new ArgumentException(
                $"Energy capacity ({request.EnergyCapacityKwh} kWh) cannot exceed the station's total capacity ({station.TotalCapacityKwh} kWh)."
            );
        }

        if (!string.IsNullOrEmpty(station.OperatingStartTime) || !string.IsNullOrEmpty(station.OperatingEndTime))
        {
            if (string.IsNullOrEmpty(station.OperatingStartTime) || string.IsNullOrEmpty(station.OperatingEndTime) ||
                string.Compare(station.OperatingStartTime, station.OperatingEndTime) >= 0)
            {
                throw new ArgumentException("Station operating schedule is invalid.");
            }

            if (TimeSpan.TryParse(station.OperatingStartTime, out var opStart) &&
                TimeSpan.TryParse(station.OperatingEndTime, out var opEnd))
            {
                if (request.StartTime < opStart || request.EndTime > opEnd)
                {
                    throw new ArgumentException("Slot times must fall within the station's operating hours.");
                }
            }
            else
            {
                throw new ArgumentException("Station operating schedule format is invalid.");
            }
        }

        var nextDate = requestedDate.AddDays(1);

        // Get active slots same station , date
        var existingSlots = await _slots
            .Find(slot =>
                slot.StationId == request.StationId &&
                slot.IsActive == true &&
                slot.SlotDate >= requestedDate &&
                slot.SlotDate < nextDate)
            .ToListAsync();

        // Check overlapping time slots
        var hasOverlap = existingSlots.Any(slot =>
            request.StartTime < slot.EndTime &&
            request.EndTime > slot.StartTime
        );

        if (hasOverlap)
        {
            throw new InvalidOperationException(
                "This station already has an overlapping time slot."
            );
        }

        // Create new slot
        var slot = new EnergyBookingSlot()
        {
            StationId = request.StationId,
            SlotDate = requestedDate,
            StartTime = request.StartTime,
            EndTime = request.EndTime,
            EnergyCapacityKwh = request.EnergyCapacityKwh,
            AvailableEnergyKwh = request.EnergyCapacityKwh,
            IsActive = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };

        await _slots.InsertOneAsync(slot);

        return slot;
    }

    //Slot update
    public async Task<EnergyBookingSlot?> UpdateAsync(
    string id,
    UpdateSlotRequest request)
    {
        if (request.StartTime >= request.EndTime)
        {
            throw new ArgumentException(
                "Start time must be before end time."
            );
        }

        if (request.EnergyCapacityKwh <= 0)
        {
            throw new ArgumentException(
                "Energy capacity must be greater than zero."
            );
        }

        var slot = await _slots
            .Find(item =>
                item.Id == id &&
                item.IsActive == true)
            .FirstOrDefaultAsync();

        if (slot is null)
        {
            return null;
        }

        var station = await _stations
            .Find(item =>
                item.Id == slot.StationId)
            .FirstOrDefaultAsync();

        if (station != null)
        {
            // Validate slot capacity does not exceed station total capacity
            if (request.EnergyCapacityKwh > station.TotalCapacityKwh)
            {
                throw new ArgumentException(
                    $"Energy capacity ({request.EnergyCapacityKwh} kWh) cannot exceed the station's total capacity ({station.TotalCapacityKwh} kWh)."
                );
            }
        }

        if (station != null && (!string.IsNullOrEmpty(station.OperatingStartTime) || !string.IsNullOrEmpty(station.OperatingEndTime)))
        {
            if (string.IsNullOrEmpty(station.OperatingStartTime) || string.IsNullOrEmpty(station.OperatingEndTime) ||
                string.Compare(station.OperatingStartTime, station.OperatingEndTime) >= 0)
            {
                throw new ArgumentException("Station operating schedule is invalid.");
            }

            if (TimeSpan.TryParse(station.OperatingStartTime, out var opStart) &&
                TimeSpan.TryParse(station.OperatingEndTime, out var opEnd))
            {
                if (request.StartTime < opStart || request.EndTime > opEnd)
                {
                    throw new ArgumentException("Slot times must fall within the station's operating hours.");
                }
            }
            else
            {
                throw new ArgumentException("Station operating schedule format is invalid.");
            }
        }

        var requestedDate = request.SlotDate.Date;
        var nextDate = requestedDate.AddDays(1);

        var existingSlots = await _slots
            .Find(item =>
                item.Id != id &&
                item.StationId == slot.StationId &&
                item.IsActive == true &&
                item.SlotDate >= requestedDate &&
                item.SlotDate < nextDate)
            .ToListAsync();

        var hasOverlap = existingSlots.Any(item =>
            request.StartTime < item.EndTime &&
            request.EndTime > item.StartTime
        );

        if (hasOverlap)
        {
            throw new InvalidOperationException(
                "This station already has an overlapping time slot."
            );
        }

        if (request.EnergyCapacityKwh < slot.EnergyCapacityKwh -
            slot.AvailableEnergyKwh)
        {
            throw new ArgumentException(
                "Updated capacity cannot be less than already reserved energy."
            );
        }

        var reservedEnergy =
            slot.EnergyCapacityKwh - slot.AvailableEnergyKwh;

        slot.SlotDate = requestedDate;
        slot.StartTime = request.StartTime;
        slot.EndTime = request.EndTime;
        slot.EnergyCapacityKwh = request.EnergyCapacityKwh;
        slot.AvailableEnergyKwh =
            request.EnergyCapacityKwh - reservedEnergy;
        slot.UpdatedAt = DateTime.UtcNow;

        await _slots.ReplaceOneAsync(
            item => item.Id == id,
            slot
        );

        return slot;
    }

    //Deactivate Slot
    public async Task<EnergyBookingSlot?> DeactivateAsync(string id)
    {
        var slot = await _slots
            .Find(item =>
                item.Id == id &&
                item.IsActive == true)
            .FirstOrDefaultAsync();

        if (slot is null)
        {
            return null;
        }

        slot.IsActive = false;
        slot.UpdatedAt = DateTime.UtcNow;

        await _slots.ReplaceOneAsync(
            item => item.Id == id,
            slot
        );

        return slot;
    }

    // Reactivate Slot
    public async Task<EnergyBookingSlot?> ReactivateAsync(string id)
    {
        var slot = await _slots
            .Find(item =>
                item.Id == id &&
                item.IsActive == false)
            .FirstOrDefaultAsync();

        if (slot is null)
        {
            return null;
        }

        var requestedDate = slot.SlotDate.Date;
        var nextDate = requestedDate.AddDays(1);

        var existingSlots = await _slots
            .Find(item =>
                item.Id != id &&
                item.StationId == slot.StationId &&
                item.IsActive == true &&
                item.SlotDate >= requestedDate &&
                item.SlotDate < nextDate)
            .ToListAsync();

        var hasOverlap = existingSlots.Any(item =>
            slot.StartTime < item.EndTime &&
            slot.EndTime > item.StartTime
        );

        if (hasOverlap)
        {
            throw new InvalidOperationException(
                "Cannot reactivate slot because it overlaps with an active slot."
            );
        }

        slot.IsActive = true;
        slot.UpdatedAt = DateTime.UtcNow;

        await _slots.ReplaceOneAsync(
            item => item.Id == id,
            slot
        );

        return slot;
    }

    // Delete Slot
    public async Task<bool> DeleteAsync(string id)
    {
        var slot = await _slots.Find(item =>
            item.Id == id).FirstOrDefaultAsync();

        if (slot is null)
            return false;

        await _slots.DeleteOneAsync(item => item.Id == id);

        return true;
    }


}