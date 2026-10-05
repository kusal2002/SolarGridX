using System.ComponentModel.DataAnnotations;

namespace SolarGridX.DTOs;

public class VerifyTransferRequest
{
    [Required, StringLength(4096)]
    public string Payload { get; set; } = string.Empty;
}

public class TransferEnergyRequest
{
    // Cumulative meter value, never a delta. Required distinguishes missing from zero.
    [Required]
    [Range(typeof(decimal), "0", "1000000")]
    public decimal? TransferredEnergyKWh { get; set; }
}

public class EndEnergyTransferRequest
{
    [Required, StringLength(500, MinimumLength = 1)]
    public string Reason { get; set; } = string.Empty;
}

public class EnergyTransferQuery
{
    [RegularExpression("^(Pending|InProgress|Completed|Cancelled|Failed)$")]
    public string? Status { get; set; }

    [RegularExpression("^[a-fA-F0-9]{24}$")]
    public string? ReservationId { get; set; }

    [StringLength(32, MinimumLength = 1)]
    public string? ProsumerNIC { get; set; }

    [RegularExpression("^[a-fA-F0-9]{24}$")]
    public string? StationId { get; set; }

    [Range(1, 1000000)]
    public int Page { get; set; } = 1;

    [Range(1, 100)]
    public int PageSize { get; set; } = 50;
}
