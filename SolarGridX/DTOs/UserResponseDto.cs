// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: UserResponseDto.cs
// Description: DTO representing user account information returned to clients.
// ============================================================================

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