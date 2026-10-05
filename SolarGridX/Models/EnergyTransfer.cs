// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: EnergyTransfer.cs
// Description: MongoDB entity representing an energy transfer session, meter readings, and audit event history.
// ============================================================================

using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Bson;

namespace SolarGridX.Models
{
    [BsonIgnoreExtraElements]
    public class EnergyTransfer
    {
        [BsonId]
        [BsonRepresentation(BsonType.ObjectId)]
        public string? id { get; set; }

        [BsonElement("reservationId")]
        public string ReservationId { get; set; } = string.Empty;

        [BsonElement("prosumerNIC")]
        public string ProsumerNIC { get; set; } = string.Empty;

        [BsonElement("stationId")]
        [BsonRepresentation(BsonType.ObjectId)]
        public string StationId { get; set; } = string.Empty;

        [BsonElement("slotId")]
        [BsonRepresentation(BsonType.ObjectId)]
        public string SlotId { get; set; } = string.Empty;

        [BsonElement("expectedEnergyKWh")]
        public decimal ExpectedEnergyKWh { get; set; }

        [BsonElement("transferredEnergyKWh")]
        public decimal TransferredEnergyKWh { get; set; } = 0;

        [BsonElement("status")]
        public string Status { get; set; } = "Pending";

        [BsonElement("createdAt")]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        [BsonElement("startedAt")]
        public DateTime? StartedAt { get; set; }

        [BsonElement("completedAt")]
        public DateTime? CompletedAt { get; set; }

        [BsonElement("endedAt")]
        public DateTime? EndedAt { get; set; }

        [BsonElement("updatedAt")]
        public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;

        [BsonElement("reason")]
        public string? Reason { get; set; }

        [BsonElement("verifiedBy")]
        public string? VerifiedBy { get; set; }

        [BsonElement("verifiedAt")]
        public DateTime? VerifiedAt { get; set; }

        [BsonElement("history")]
        public List<EnergyTransferEvent> History { get; set; } = [];
    }

    public class EnergyTransferEvent
    {
        public string Action { get; set; } = string.Empty;
        public string Status { get; set; } = string.Empty;
        public decimal TransferredEnergyKWh { get; set; }
        public DateTime At { get; set; }
        public string ActorNIC { get; set; } = string.Empty;
        public string? Reason { get; set; }
    }
}
