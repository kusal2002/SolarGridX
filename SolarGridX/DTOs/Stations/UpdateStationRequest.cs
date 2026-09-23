namespace SolarGridX.DTOs.Stations;

public class UpdateStationRequest
{
    public string StationName { get; set; } = string.Empty;

    public string Location { get; set; } = string.Empty;

    public double Latitude { get; set; }

    public double Longitude { get; set; }

    public double TotalCapacityKwh { get; set; }
}