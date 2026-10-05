// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: RegisterUserDto.cs
// Description: DTO for registering a new prosumer account with validation rules.
// ============================================================================

using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class RegisterUserDto
    {
        [Required]
        public string NIC { get; set; } = string.Empty;

        [Required]
        [StringLength(100)]
        public string Name { get; set; } = string.Empty;

        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;

        [Required]
        [MinLength(8)]
        public string Password { get; set; } = string.Empty;
    }
}