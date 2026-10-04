using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class CreateEnergyTransferRequest
    {
        [Required]
        [RegularExpression("^[a-fA-F0-9]{24}$")]
        public string ReservationId { get; set; } = string.Empty;

        [Required]
        [StringLength(32, MinimumLength = 1)]
        public string SellerId { get; set; } = string.Empty;

        [Required]
        [StringLength(32, MinimumLength = 1)]
        public string BuyerId { get; set; } = string.Empty;

        [Range(typeof(decimal), "0.001", "1000000")]
        public decimal ExpectedEnergyKWh { get; set; }
    }
}
