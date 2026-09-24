using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs.Reservations;

public class UpdateReservationStatusRequest
{
    [Required]
    public string Status { get; set; } = string.Empty;
}
