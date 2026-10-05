// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: EnergyTransfersController.cs
// Description: API controller for energy transfer verification, charging lifecycle management, and meter tracking.
// ============================================================================

using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[Route("api/energy-transfers")]
[Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
[ApiController]
public class EnergyTransfersController(EnergyTransferService service, ReservationQrService qr, ReservationService reservations, StationAccessService access) : ControllerBase
{
    [HttpPost("verify")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Verify(VerifyTransferRequest request, CancellationToken ct) => Execute(async () =>
    {
        // Read scanned QR ticket, validate station access, and create or verify the energy transfer
        var ticket = qr.Read(request.Payload);
        var reservation = await service.GetReservationByIdAsync(ticket.ReservationId)
            ?? throw new TransferException(404, "Reservation not found.");
        if (!await access.CanAccessAsync(User, reservation.StationId)) return Forbid();
        if (!double.IsFinite(reservation.RequestedEnergyKwh) || reservation.RequestedEnergyKwh < 0.001 || reservation.RequestedEnergyKwh > 1000000)
            throw new TransferException(400, "Reservation has invalid energy.");
        var transfer = await service.CreateAsync(new CreateEnergyTransferRequest
        {
            ReservationId = reservation.Id
        }, Actor, ct, ticket);
        return Ok(transfer);
    });
    [HttpGet]
    public Task<IActionResult> GetAll([FromQuery] EnergyTransferQuery query, CancellationToken ct = default) =>
        Execute(async () =>
        {
            // Retrieve energy transfers scoped to operator assigned stations or prosumer ownership
            IEnumerable<string>? allowed = null;
            if (query.StationId != null && !await access.CanAccessAsync(User, query.StationId)) return Forbid();
            if (!User.IsInRole("Backoffice"))
            {
                var list = User.IsInRole("Prosumer")
                    ? await reservations.GetByProsumerAsync(Actor) : await reservations.GetAllAsync();
                var ids = await access.StationIdsAsync(User);
                if (ids != null) list = list.Where(r => ids.Contains(r.StationId)).ToList();
                allowed = list.Select(r => r.Id);
            }
            if (query.StationId != null)
            {
                var selectedStation = query.StationId;
                var selected = (await reservations.GetAllAsync()).Where(r => r.StationId == selectedStation).Select(r => r.Id).ToList();
                allowed = allowed == null ? selected : allowed.Intersect(selected);
                query.StationId = null;
            }
            return Ok(await service.GetAllAsync(query, ct, allowed));
        });

    [HttpGet("{id}")]
    public Task<IActionResult> GetById(string id, CancellationToken ct = default) => Execute(async () =>
    {
        // Fetch specific energy transfer details after verifying user authorization
        var transfer = await service.GetByIdAsync(id, ct);
        if (transfer != null && !await CanRead(transfer)) return Forbid();
        return transfer == null ? NotFound(new { message = "Energy transfer not found." }) : Ok(transfer);
    });

    [HttpGet("{id}/history")]
    public Task<IActionResult> GetHistory(string id, CancellationToken ct = default) => Execute(async () =>
    {
        // Retrieve audit event log history for the specified energy transfer
        var transfer = await service.GetByIdAsync(id, ct);
        if (transfer != null && !await CanRead(transfer)) return Forbid();
        return transfer == null ? NotFound(new { message = "Energy transfer not found." }) : Ok(transfer.History);
    });

    [HttpPost]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Create([FromBody] CreateEnergyTransferRequest request, CancellationToken ct = default) => Execute(async () =>
    {
        // Create an energy transfer record for an approved reservation
        var reservation = await service.GetReservationByIdAsync(request.ReservationId);
        if (reservation != null && !await access.CanAccessAsync(User, reservation.StationId)) return Forbid();
        var transfer = await service.CreateAsync(request, Actor, ct);
        return CreatedAtAction(nameof(GetById), new { id = transfer.id }, transfer);
    });

    [HttpPatch("{id}/start")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Start(string id, CancellationToken ct = default)
    {
        // Start physical energy transfer session
        return Change(id, "start", null, null, ct);
    }

    [HttpPatch("{id}/progress")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Progress(string id, [FromBody] TransferEnergyRequest request, CancellationToken ct = default)
    {
        // Update cumulative transferred energy meter reading
        return Change(id, "progress", request.TransferredEnergyKWh, null, ct);
    }

    [HttpPatch("{id}/complete")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Complete(string id, [FromBody] TransferEnergyRequest request, CancellationToken ct = default)
    {
        // Mark transfer completed when full requested energy has been delivered
        return Change(id, "complete", request.TransferredEnergyKWh, null, ct);
    }

    [HttpPatch("{id}/cancel")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Cancel(string id, [FromBody] EndEnergyTransferRequest request, CancellationToken ct = default)
    {
        // Cancel energy transfer before it starts and restore slot capacity
        return Change(id, "cancel", null, request.Reason, ct);
    }

    [HttpPatch("{id}/fail")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public Task<IActionResult> Fail(string id, [FromBody] EndEnergyTransferRequest request, CancellationToken ct = default)
    {
        // Mark an active transfer as failed and return any unused energy back to the slot
        return Change(id, "fail", null, request.Reason, ct);
    }

    private string Actor => User.FindFirstValue(ClaimTypes.NameIdentifier)!;

    private Task<IActionResult> Change(string id, string action, decimal? energy, string? reason, CancellationToken ct) =>
        Execute(async () =>
        {
            // Execute state transition on the transfer service with permission validation
            var transfer = await service.GetByIdAsync(id, ct);
            if (transfer != null && !await CanRead(transfer)) return Forbid();
            return Ok(await service.ChangeAsync(id, action, energy, reason, Actor, ct));
        });

    private async Task<bool> CanRead(Models.EnergyTransfer transfer)
    {
        // Verify whether the caller has permissions to view this transfer record
        if (User.IsInRole("Backoffice")) return true;
        var reservation = await service.GetReservationByIdAsync(transfer.ReservationId);
        if (reservation == null) return false;
        return User.IsInRole("Prosumer") ? reservation.ProsumerNIC == Actor : await access.CanAccessAsync(User, reservation.StationId);
    }

    private async Task<IActionResult> Execute(Func<Task<IActionResult>> operation)
    {
        // Wrapper to catch TransferException and return appropriate HTTP status code
        try { return await operation(); }
        catch (TransferException ex) { return StatusCode(ex.StatusCode, new { message = ex.Message }); }
    }
}
