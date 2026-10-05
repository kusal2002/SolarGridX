// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: SolarStation.cs
// Description: MongoDB entity representing a physical solar microgrid charging station.
// ============================================================================

using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGridX.Models;

[BsonIgnoreExtraElements]
public class SolarStation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    public string StationName { get; set; } = string.Empty;

    [BsonElement("operatorNIC")]
    public string? OperatorNIC { get; set; }

    [BsonElement("operatorNICs")]
    public List<string> OperatorNICs { get; set; } = [];

    public string Location { get; set; } = string.Empty;

    public double Latitude { get; set; }

    public double Longitude { get; set; }

    public double TotalCapacityKwh { get; set; }

    public string OperatingStartTime { get; set; } = string.Empty;

    public string OperatingEndTime { get; set; } = string.Empty;

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
