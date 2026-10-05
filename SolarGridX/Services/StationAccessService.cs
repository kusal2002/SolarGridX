using System.Security.Claims;
using MongoDB.Driver;
using SolarGridX.Models;

namespace SolarGridX.Services;

public class StationAccessService(IMongoDatabase database)
{
    public async Task<List<string>?> StationIdsAsync(ClaimsPrincipal user)
    {
        if (!user.IsInRole("Grid Operator") || user.IsInRole("Backoffice")) return null;
        var nic = user.FindFirstValue(ClaimTypes.NameIdentifier);
        if (string.IsNullOrWhiteSpace(nic)) return [];
        return await database.GetCollection<SolarStation>("SolarStationInfo")
            .Find(s => s.OperatorNIC == nic).Project(s => s.Id).ToListAsync();
    }

    public async Task<bool> CanAccessAsync(ClaimsPrincipal user, string stationId)
    {
        var ids = await StationIdsAsync(user);
        return ids == null || (MongoDB.Bson.ObjectId.TryParse(stationId, out var id) && ids.Contains(id.ToString()));
    }

    public async Task<bool> CanAccessSlotAsync(ClaimsPrincipal user, string slotId)
    {
        if (!user.IsInRole("Grid Operator") || user.IsInRole("Backoffice")) return true;
        var slot = await database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots").Find(s => s.Id == slotId).FirstOrDefaultAsync();
        return slot != null && await CanAccessAsync(user, slot.StationId);
    }
}
