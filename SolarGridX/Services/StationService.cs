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

    //Get all stations
    public async Task<List<SolarStation>> GetAllAsync()
    {
        return await _stations
            .Find(station => station.IsActive == true)
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
        var station = new SolarStation()
        {
            StationName = request.StationName,
            Location = request.Location,
            Latitude = request.Latitude,
            Longitude = request.Longitude,
            TotalCapacityKwh = request.TotalCapacityKwh,
            IsActive = true,
            CreatedAt = DateTime.UtcNow,
            UpdatedAt = DateTime.UtcNow
        };
        await _stations.InsertOneAsync(station);

        return station;
    }

    //Update Stations
    public async Task<SolarStation?> UpdateAsync(
    string id,
    UpdateStationRequest request)
    {
        var station = await GetByIdAsync(id);

        if (station is null)
        {
            return null;
        }

        station.StationName = request.StationName;
        station.Location = request.Location;
        station.Latitude = request.Latitude;
        station.Longitude = request.Longitude;
        station.TotalCapacityKwh = request.TotalCapacityKwh;
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


}