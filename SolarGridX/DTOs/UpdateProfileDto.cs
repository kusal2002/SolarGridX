// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: UpdateProfileDto.cs
// Description: DTO for updating user profile details (name and email).
// ============================================================================

using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class UpdateProfileDto
    {
        [Required]
        [StringLength(100)]
        public string Name { get; set; } = string.Empty;

        [Required]
        [EmailAddress]
        public string Email { get; set; } = string.Empty;
    }
}