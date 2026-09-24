using MongoDB.Bson;
using MongoDB.Bson.Serialization.Attributes;

namespace SolarGridX.Models;

public class EnergyReservation
{
    [BsonId]
    [BsonRepresentation(BsonType.ObjectId)]
    public string Id { get; set; } = string.Empty;

    // Connects to Member 1: User's NIC
    [BsonElement("prosumerNIC")]
    public string ProsumerNIC { get; set; } = string.Empty;

    // Connects to Member 2: Station and Slot
    [BsonRepresentation(BsonType.ObjectId)]
    [BsonElement("stationId")]
    public string StationId { get; set; } = string.Empty;

    [BsonRepresentation(BsonType.ObjectId)]
    [BsonElement("slotId")]
    public string SlotId { get; set; } = string.Empty;

    [BsonElement("reservationDate")]
    public DateTime ReservationDate { get; set; }

    [BsonElement("startTime")]
    public TimeSpan StartTime { get; set; }

    [BsonElement("endTime")]
    public TimeSpan EndTime { get; set; }

    [BsonElement("requestedEnergyKwh")]
    public double RequestedEnergyKwh { get; set; }

    // Possible values: "Pending", "Approved", "Cancelled", "Completed"
    [BsonElement("status")]
    public string Status { get; set; } = "Pending";

    [BsonElement("cancellationReason")]
    public string? CancellationReason { get; set; }

    [BsonElement("createdAt")]
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

    [BsonElement("updatedAt")]
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
