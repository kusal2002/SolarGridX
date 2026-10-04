using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs.Reservations;

public class UpdateReservationRequest
{
    public string? NewSlotId { get; set; }

    [Range(0.1, 100000, ErrorMessage = "Requested energy must be greater than zero.")]
    public double? RequestedEnergyKwh { get; set; }
}
