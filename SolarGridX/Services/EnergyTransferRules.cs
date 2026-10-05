// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: EnergyTransferRules.cs
// Description: Business rules and state transition validation for energy transfer sessions.
// ============================================================================

using SolarGridX.Models;

namespace SolarGridX.Services;

public sealed class TransferException(int statusCode, string message) : Exception(message)
{
    public int StatusCode { get; } = statusCode;
}

public static class EnergyTransferRules
{
    public static void Apply(EnergyTransfer transfer, string action, decimal? energy, string? reason, string actor, DateTime now)
    {
        // Enforce state transition rules, meter readings, and record audit history
        var allowed = action switch
        {
            "start" or "cancel" => transfer.Status == "Pending",
            "progress" or "complete" or "fail" => transfer.Status == "InProgress",
            _ => false
        };
        if (!allowed)
            throw new TransferException(409, $"Cannot {action} a transfer with status '{transfer.Status}'.");

        if (action is "cancel" or "fail" && (string.IsNullOrWhiteSpace(reason) || reason.Trim().Length > 500))
            throw new TransferException(400, "A reason of 1 to 500 characters is required.");

        if (action is "progress" or "complete")
        {
            if (energy is null || energy < transfer.TransferredEnergyKWh || energy > transfer.ExpectedEnergyKWh || energy < 0)
                throw new TransferException(400, "Cumulative energy must not decrease or exceed expected energy.");
            if (action == "complete" && energy != transfer.ExpectedEnergyKWh)
                throw new TransferException(400, "Completion requires the full expected energy. Use fail for a partial transfer.");
            if (action == "progress" && energy == transfer.TransferredEnergyKWh) return;
            transfer.TransferredEnergyKWh = energy.Value;
        }

        transfer.Status = action switch
        {
            "start" => "InProgress",
            "complete" => "Completed",
            "cancel" => "Cancelled",
            "fail" => "Failed",
            _ => transfer.Status
        };
        if (action == "start") transfer.StartedAt = now;
        if (action == "complete") transfer.CompletedAt = now;
        if (action is "complete" or "cancel" or "fail") transfer.EndedAt = now;
        if (action is "cancel" or "fail") transfer.Reason = reason!.Trim();
        transfer.UpdatedAt = now;
        transfer.History.Add(new EnergyTransferEvent
        {
            Action = action, Status = transfer.Status, TransferredEnergyKWh = transfer.TransferredEnergyKWh,
            ActorNIC = actor, At = now, Reason = transfer.Reason
        });
    }
}
