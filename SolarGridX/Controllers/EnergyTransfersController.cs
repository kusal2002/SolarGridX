using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[Route("api/energy-transfers")]
[Authorize(Roles = "Backoffice,Grid Operator")]
[ApiController]
public class EnergyTransfersController(EnergyTransferService service, ReservationQrService qr) : ControllerBase
{
    [HttpPost("verify")]
    public Task<IActionResult> Verify(VerifyTransferRequest request, CancellationToken ct) => Execute(async () =>
    {
        var ticket = qr.Read(request.Payload);
        var reservation = await service.GetReservationByIdAsync(ticket.ReservationId)
            ?? throw new TransferException(404, "Reservation not found.");
        if (!double.IsFinite(reservation.RequestedEnergyKwh) || reservation.RequestedEnergyKwh < 0.001 || reservation.RequestedEnergyKwh > 1000000)
            throw new TransferException(400, "Reservation has invalid energy.");
        var transfer = await service.CreateAsync(new CreateEnergyTransferRequest
        {
            ReservationId = reservation.Id, SellerId = request.SellerNIC,
            BuyerId = reservation.ProsumerNIC, ExpectedEnergyKWh = (decimal)reservation.RequestedEnergyKwh
        }, Actor, ct, ticket);
        return Ok(transfer);
    });
    [HttpGet]
    public Task<IActionResult> GetAll([FromQuery] EnergyTransferQuery query, CancellationToken ct = default) =>
        Execute(async () => Ok(await service.GetAllAsync(query, ct)));

    [HttpGet("{id}")]
    public Task<IActionResult> GetById(string id, CancellationToken ct = default) => Execute(async () =>
    {
        var transfer = await service.GetByIdAsync(id, ct);
        return transfer == null ? NotFound(new { message = "Energy transfer not found." }) : Ok(transfer);
    });

    [HttpGet("{id}/history")]
    public Task<IActionResult> GetHistory(string id, CancellationToken ct = default) => Execute(async () =>
    {
        var transfer = await service.GetByIdAsync(id, ct);
        return transfer == null ? NotFound(new { message = "Energy transfer not found." }) : Ok(transfer.History);
    });

    [HttpPost]
    public Task<IActionResult> Create([FromBody] CreateEnergyTransferRequest request, CancellationToken ct = default) => Execute(async () =>
    {
        var transfer = await service.CreateAsync(request, Actor, ct);
        return CreatedAtAction(nameof(GetById), new { id = transfer.id }, transfer);
    });

    [HttpPatch("{id}/start")]
    public Task<IActionResult> Start(string id, CancellationToken ct = default) => Change(id, "start", null, null, ct);

    [HttpPatch("{id}/progress")]
    public Task<IActionResult> Progress(string id, [FromBody] TransferEnergyRequest request, CancellationToken ct = default) =>
        Change(id, "progress", request.TransferredEnergyKWh, null, ct);

    [HttpPatch("{id}/complete")]
    public Task<IActionResult> Complete(string id, [FromBody] TransferEnergyRequest request, CancellationToken ct = default) =>
        Change(id, "complete", request.TransferredEnergyKWh, null, ct);

    [HttpPatch("{id}/cancel")]
    public Task<IActionResult> Cancel(string id, [FromBody] EndEnergyTransferRequest request, CancellationToken ct = default) =>
        Change(id, "cancel", null, request.Reason, ct);

    [HttpPatch("{id}/fail")]
    public Task<IActionResult> Fail(string id, [FromBody] EndEnergyTransferRequest request, CancellationToken ct = default) =>
        Change(id, "fail", null, request.Reason, ct);

    private string Actor => User.FindFirstValue(ClaimTypes.NameIdentifier)!;

    private Task<IActionResult> Change(string id, string action, decimal? energy, string? reason, CancellationToken ct) =>
        Execute(async () => Ok(await service.ChangeAsync(id, action, energy, reason, Actor, ct)));

    private async Task<IActionResult> Execute(Func<Task<IActionResult>> operation)
    {
        try { return await operation(); }
        catch (TransferException ex) { return StatusCode(ex.StatusCode, new { message = ex.Message }); }
    }
}
