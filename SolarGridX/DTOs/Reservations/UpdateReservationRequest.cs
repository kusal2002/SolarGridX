// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: UpdateReservationRequest.cs
// Description: DTO for modifying an existing energy reservation (e.g. changing slot or requested kWh).
// ============================================================================

using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs.Reservations;

public class UpdateReservationRequest
{
    public string? NewSlotId { get; set; }

    [Range(0.1, 100000, ErrorMessage = "Requested energy must be greater than zero.")]
    public double? RequestedEnergyKwh { get; set; }
}
