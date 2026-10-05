// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: CreateSlotRequest.cs
// Description: DTO containing slot schedule and capacity details for creating a new booking slot.
// ============================================================================

namespace SolarGridX.DTOs.Slots;

public class CreateSlotRequest
{
    public string StationId { get; set; } = string.Empty;

    public DateTime SlotDate { get; set; }

    public TimeSpan StartTime { get; set; }

    public TimeSpan EndTime { get; set; }

    public double EnergyCapacityKwh { get; set; }
}