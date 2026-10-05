using MongoDB.Driver;
using SolarGridX.DTOs.Stations;
using SolarGridX.Models;

namespace SolarGridX.Services;

public class StationService
{
    private readonly IMongoCollection<SolarStation> _stations;

    public StationService(IMongoDatabase database)
    {
        _stations = database.GetCollection<SolarStation>("SolarStationInfo");
    }

    public Task<List<SolarStation>> GetByIdsAsync(IEnumerable<string> ids) =>
        _stations.Find(Builders<SolarStation>.Filter.In(station => station.Id, ids)).ToListAsync();

    public Task<List<User>> GetOperatorsAsync() => _stations.Database.GetCollection<User>("Users")
        .Find(u => u.Role == "Grid Operator" && u.AccountStatus == AccountStatus.Active).ToListAsync();

    public async Task<SolarStation?> AssignOperatorAsync(string id, string? nic)
    {
        if (!MongoDB.Bson.ObjectId.TryParse(id, out _)) throw new ArgumentException("Invalid station ID.");
        nic = string.IsNullOrWhiteSpace(nic) ? null : nic.Trim();
        if (nic != null && !(await GetOperatorsAsync()).Any(u => u.NIC == nic))
            throw new ArgumentException("Choose an active Grid Operator account.");
        return await _stations.FindOneAndUpdateAsync(s => s.Id == id,
            Builders<SolarStation>.Update.Set(s => s.OperatorNIC, nic).Set(s => s.UpdatedAt, DateTime.UtcNow),
            new FindOneAndUpdateOptions<SolarStation> { ReturnDocument = ReturnDocument.After });
    }

    //Get all stations
    public async Task<List<SolarStation>> GetAllAsync()
    {
        return await _stations
            .Find(station => station.IsActive == true)
            .ToListAsync();
    }

    // Get all stations including inactive stations
    public async Task<List<SolarStation>> GetAllIncludingInactiveAsync()
    {
        return await _stations
            .Find(_ => true)
            .ToListAsync();
    }

    //Get stations by Id
    public async Task<SolarStation?> GetByIdAsync(string id)
    {
        return await _stations
            .Find(station =>
                station.Id == id &&
                station.IsActive == true)
            .FirstOrDefaultAsync();
    }

    //Create Stations
    public async Task<SolarStation> CreateAsync(
        CreateStationRequest request)
    {
        ValidateSchedule(request.OperatingStartTime, request.OperatingEndTime);

        var station = new SolarStation()
        {
            StationName = request.StationName,
            Location = request.Location,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            TotalCapacityKwh = request.TotalCapacityKwh,
            OperatingStartTime = request.OperatingStartTime,
            OperatingEndTime = request.OperatingEndTime,
            IsActive = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };
        await _stations.InsertOneAsync(station);

        return station;
    }

    public async Task<SolarStation?> UpdateAsync(
    string id,
    UpdateStationRequest request)
    {
        ValidateSchedule(request.OperatingStartTime, request.OperatingEndTime);

        var station = await _stations
            .Find(s => s.Id == id)
            .FirstOrDefaultAsync();

        if (station is null)
        {
            return null;
        }

        station.StationName = request.StationName;
        station.Location = request.Location;
        station.Latitude = request.Latitude;
        station.Longitude = request.Longitude;
        station.TotalCapacityKwh = request.TotalCapacityKwh;
        station.OperatingStartTime = request.OperatingStartTime;
        station.OperatingEndTime = request.OperatingEndTime;
        station.IsActive = request.IsActive;
        station.UpdatedAt = DateTime.UtcNow;

        await _stations.ReplaceOneAsync(
            item => item.Id == id,
            station
        );

        return station;
    }

    //Delete or Deactive Stations
    public async Task<bool> DeactivateAsync(string id)
    {
        var _reservations = _stations.Database.GetCollection<EnergyReservation>("EnergyReservations");
        var activeReservationsCount = await _reservations.CountDocumentsAsync(
            r => r.StationId == id && (r.Status == "Pending" || r.Status == "Approved")
        );

        if (activeReservationsCount > 0)
        {
            throw new InvalidOperationException("Cannot deactivate a station with active energy reservations.");
        }

        var update = Builders<SolarStation>.Update
            .Set(station => station.IsActive, false)
            .Set(station => station.UpdatedAt, DateTime.UtcNow);

        var result = await _stations.UpdateOneAsync(
            station => station.Id == id,
            update
        );

        return result.MatchedCount > 0;
    }

    //Reactive stations
    public async Task<bool> ReactivateAsync(string id)
    {
        var update = Builders<SolarStation>.Update
            .Set(station => station.IsActive, true)
            .Set(station => station.UpdatedAt, DateTime.UtcNow);

        var result = await _stations.UpdateOneAsync(
            station => station.Id == id,
            update
        );

        return result.MatchedCount > 0;
    }

    private void ValidateSchedule(string startTimeStr, string endTimeStr)
    {
        if (!string.IsNullOrEmpty(startTimeStr) && !string.IsNullOrEmpty(endTimeStr))
        {
            if (TimeSpan.TryParse(startTimeStr, out var startTime) && 
                TimeSpan.TryParse(endTimeStr, out var endTime))
            {
                if (startTime >= endTime)
                {
                    throw new ArgumentException("Operating start time must be before operating end time.");
                }
            }
            else 
            {
                throw new ArgumentException("Invalid operating time format.");
            }
        }
        else if (!string.IsNullOrEmpty(startTimeStr) || !string.IsNullOrEmpty(endTimeStr))
        {
            throw new ArgumentException("Both operating start time and end time must be provided if schedule is set.");
        }
    }
}
