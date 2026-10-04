namespace SolarGridX.DTOs
{
    public class UserResponseDto
    {
        public string NIC { get; set; } = string.Empty;

        public string Name { get; set; } = string.Empty;

        public string Email { get; set; } = string.Empty;

        public string Role { get; set; } = string.Empty;

        public string AccountStatus { get; set; } = string.Empty;

        public DateTime CreatedAt { get; set; }

        public DateTime? DeactivationRequestedAt { get; set; }
    }
}