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

    // Create slot
    public async Task<EnergyBookingSlot?> CreateAsync(
        CreateSlotRequest request)
    {
        var station = await _stations
            .Find(item =>
                item.Id == request.StationId &&
                item.IsActive == true)
            .FirstOrDefaultAsync();

        if (station is null)
        {
            return null;
        }

        var slot = new EnergyBookingSlot()
        {
            StationId = request.StationId,
            SlotDate = request.SlotDate,
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
}