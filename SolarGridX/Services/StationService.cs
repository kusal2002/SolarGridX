// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: StationService.cs
// Description: Service class handling business logic for solar charging stations and operator assignments.
// ============================================================================

using MongoDB.Driver;
using SolarGridX.DTOs.Stations;
using SolarGridX.Models;

namespace SolarGridX.Services;

public class StationService
{
    private readonly IMongoCollection<SolarStation> _stations;

    public StationService(IMongoDatabase database)
    {
        // Initialize Mongo collection for solar stations
        _stations = database.GetCollection<SolarStation>("SolarStationInfo");
    }

    public Task<List<SolarStation>> GetByIdsAsync(IEnumerable<string> ids)
    {
        // Fetch multiple solar stations by their unique MongoDB IDs
        return _stations.Find(Builders<SolarStation>.Filter.In(station => station.Id, ids)).ToListAsync();
    }

    public Task<List<User>> GetOperatorsAsync()
    {
        // Retrieve list of active grid operators from the users collection
        return _stations.Database.GetCollection<User>("Users")
            .Find(u => u.Role == "Grid Operator" && u.AccountStatus == AccountStatus.Active).ToListAsync();
    }

    private async Task<List<string>> ValidateOperatorsAsync(IEnumerable<string>? requested)
    {
        // Validate that requested operator NICs exist and belong to active Grid Operators
        var nics = (requested ?? []).Where(n => !string.IsNullOrWhiteSpace(n)).Select(n => n.Trim()).Distinct().ToList();
        var active = (await GetOperatorsAsync()).Select(u => u.NIC).ToHashSet();
        if (nics.Any(n => !active.Contains(n))) throw new ArgumentException("Choose active Grid Operator accounts only.");
        return nics;
    }

    public Task<SolarStation?> AssignOperatorAsync(string id, string? nic)
    {
        // Assign a single operator to a station
        return AssignOperatorsAsync(id, string.IsNullOrWhiteSpace(nic) ? [] : [nic]);
    }

    public async Task<SolarStation?> AssignOperatorsAsync(string id, IEnumerable<string>? requested)
    {
        // Assign multiple grid operators to a station and update the station document
        if (!MongoDB.Bson.ObjectId.TryParse(id, out _)) throw new ArgumentException("Invalid station ID.");
        var nics = await ValidateOperatorsAsync(requested);
        return await _stations.FindOneAndUpdateAsync(s => s.Id == id,
            Builders<SolarStation>.Update.Set(s => s.OperatorNICs, nics).Set(s => s.OperatorNIC, nics.FirstOrDefault()).Set(s => s.UpdatedAt, DateTime.UtcNow),
            new FindOneAndUpdateOptions<SolarStation> { ReturnDocument = ReturnDocument.After });
    }

    //Get all stations
    public async Task<List<SolarStation>> GetAllAsync()
    {
        // Fetch all active solar charging stations
        return await _stations
            .Find(station => station.IsActive == true)
            .ToListAsync();
    }

    // Get all stations including inactive stations
    public async Task<List<SolarStation>> GetAllIncludingInactiveAsync()
    {
        // Fetch all stations from database including inactive ones
        return await _stations
            .Find(_ => true)
            .ToListAsync();
    }

    //Get stations by Id
    public async Task<SolarStation?> GetByIdAsync(string id)
    {
        // Find a specific active solar station by its ID
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
        // Validate operating schedule and create a new solar station in the database
        ValidateSchedule(request.OperatingStartTime, request.OperatingEndTime);

        var operatorNICs = await ValidateOperatorsAsync(request.OperatorNICs);
        var station = new SolarStation()
        {
            StationName = request.StationName,
            OperatorNICs = operatorNICs,
            OperatorNIC = operatorNICs.FirstOrDefault(),
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
        // Validate operating schedule and update existing station details
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
        // Check for active reservations before deactivating a station
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
        // Reactivate a previously deactivated solar station
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
        // Validate operating schedule times and ensure start time is before end time
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
