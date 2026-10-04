namespace SolarGridX.DTOs.Slots;

public class CreateSlotRequest
{
    public string StationId { get; set; } = string.Empty;

    public DateTime SlotDate { get; set; }

    public TimeSpan StartTime { get; set; }

    public TimeSpan EndTime { get; set; }

    public double EnergyCapacityKwh { get; set; }
}