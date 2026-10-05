using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class CreateEnergyTransferRequest
    {
        [Required]
        [RegularExpression("^[a-fA-F0-9]{24}$")]
        public string ReservationId { get; set; } = string.Empty;

    }
}
