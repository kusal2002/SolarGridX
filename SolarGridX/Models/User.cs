using MongoDB.Bson.Serialization.Attributes;

namespace SolarGridX.Models
{
    public class User
    {
        [BsonId]
        [BsonElement("_id")]
        public string NIC { get; set; } = string.Empty;

        [BsonElement("name")]
        public string Name { get; set; } = string.Empty;

        [BsonElement("email")]
        public string Email { get; set; } = string.Empty;

        [BsonElement("passwordHash")]
        public string PasswordHash { get; set; } = string.Empty;

        [BsonElement("role")]
        public string Role { get; set; } = "Prosumer";

        [BsonElement("accountStatus")]
        public string AccountStatus { get; set; } = "Active";

        [BsonElement("createdAt")]
        public DateTime CreatedAt { get; set; } = DateTime.UtcNow;

        [BsonElement("deactivationRequestedAt")]
        public DateTime? DeactivationRequestedAt { get; set; }

        [BsonElement("securityStamp")]
        public string SecurityStamp { get; set; } = string.Empty;
    }
}