// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: CreateEnergyTransferRequest.cs
// Description: DTO containing the reservation ID required to initiate an energy transfer.
// ============================================================================

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
