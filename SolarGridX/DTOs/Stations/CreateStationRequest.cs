namespace SolarGridX.DTOs.Stations;

public class CreateStationRequest
{
    public List<string> OperatorNICs { get; set; } = [];

    public string StationName { get; set; } = string.Empty;

    public string Location { get; set; } = string.Empty;

    public double Latitude { get; set; }

    public double Longitude { get; set; }

    public double TotalCapacityKwh { get; set; }

    public string OperatingStartTime { get; set; } = string.Empty;

    public string OperatingEndTime { get; set; } = string.Empty;
}