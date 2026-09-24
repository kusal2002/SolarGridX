using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGridX.Models;

public class EnergyBookingSlot
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.ObjectId)]
    public string StationId { get; set; } = string.Empty;

    public DateTime SlotDate { get; set; }

    public TimeSpan StartTime { get; set; }

    public TimeSpan EndTime { get; set; }

    public double EnergyCapacityKwh { get; set; }

    public double AvailableEnergyKwh { get; set; }

    public bool IsActive { get; set; } = true;

    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

}