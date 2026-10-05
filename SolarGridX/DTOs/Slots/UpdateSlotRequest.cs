// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: UpdateSlotRequest.cs
// Description: DTO for updating slot date, start/end time, and energy capacity.
// ============================================================================

namespace SolarGridX.DTOs.Slots;

public class UpdateSlotRequest
{
    public DateTime SlotDate { get; set; }

    public TimeSpan StartTime { get; set; }

    public TimeSpan EndTime { get; set; }

    public double EnergyCapacityKwh { get; set; }
}