using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class CreateStaffUserDto
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

        [Required]
        [RegularExpression("^(Backoffice|Grid Operator)$")]
        public string Role { get; set; } = string.Empty;
    }
}
