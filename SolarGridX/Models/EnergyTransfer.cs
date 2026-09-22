using MongoDB.Bson.Serialization.Attributes;
using MongoDB.Bson;


namespace SolarGridX.Models
{
    public class EnergyTransfer
    {
        [BsonId]
        [BsonRepresentation(BsonType.ObjectId)]
        public string?id { get; set; }

        [BsonElement("reservationId")]
        public string ReservationId { get; set; } = string.Empty;

        [BsonElement("sellerId")]
        public string SellerId { get; set; } = string.Empty;

        [BsonElement("buyerId")]
        public string BuyerId { get; set; } = string.Empty;

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
    }
}
