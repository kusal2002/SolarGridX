// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: UpdateReservationStatusRequest.cs
// Description: DTO for updating reservation status (e.g. approving a pending reservation).
// ============================================================================

using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs.Reservations;

public class UpdateReservationStatusRequest
{
    [Required]
    public string Status { get; set; } = string.Empty;
}
