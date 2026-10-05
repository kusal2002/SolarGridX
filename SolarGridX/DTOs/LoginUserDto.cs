// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: LoginUserDto.cs
// Description: DTO containing user login credentials (email and password).
// ============================================================================

using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class LoginUserDto
    {
        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;

        [Required]
        public string Password { get; set; } = string.Empty;
    }
}