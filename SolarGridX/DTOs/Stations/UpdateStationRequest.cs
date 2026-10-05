// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: UpdateStationRequest.cs
// Description: DTO containing updated station parameters such as capacity, location, and status.
// ============================================================================

namespace SolarGridX.DTOs.Stations;

public class UpdateStationRequest
{
    public string StationName { get; set; } = string.Empty;

    public string Location { get; set; } = string.Empty;

    public double Latitude { get; set; }

    public double Longitude { get; set; }

    public double TotalCapacityKwh { get; set; }

    public string OperatingStartTime { get; set; } = string.Empty;

    public string OperatingEndTime { get; set; } = string.Empty;

    public bool IsActive { get; set; }
}