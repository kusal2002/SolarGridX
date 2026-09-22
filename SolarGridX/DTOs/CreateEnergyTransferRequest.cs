using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs
{
    public class CreateEnergyTransferRequest
    {
        [Required]
        [MinLength(1)]
        public string ReservationId { get; set; } = string.Empty;

        [Required]
        [MinLength(1)]
        public string SellerId { get; set; } = string.Empty;

        [Required]
        [MinLength(1)]
        public string BuyerId { get; set; } = string.Empty;

        [Range(typeof(decimal), "0.001", "1000000")]
        public decimal ExpectedEnergyKWh { get; set; }
    }
}
