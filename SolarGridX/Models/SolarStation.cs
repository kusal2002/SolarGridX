using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;
using static System.Runtime.InteropServices.JavaScript.JSType;

namespace SolarGridX.Models;

public class SolarStation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]

    public string Id { get; set; } = string.Empty;

    public string StationName { get; set; } = string.Empty;

    public string Location { get; set; } = string.Empty;

    public double Latitude { get; set; }

    public double Longitude { get; set; }

    public double TotalCapacityKwh { get; set; }

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}