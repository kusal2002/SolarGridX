using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs.Reservations;

public class CreateReservationRequest
{
    [Required]
    public string ProsumerNIC { get; set; } = string.Empty;

    [Required]
    public string SlotId { get; set; } = string.Empty;

    [Range(0.1, 100000, ErrorMessage = "Requested energy must be greater than zero.")]
    public double RequestedEnergyKwh { get; set; }
}
