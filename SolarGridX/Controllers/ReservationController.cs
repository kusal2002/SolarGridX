using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs.Reservations;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
[ApiController]
[Route("api/[controller]")]
public class ReservationController : ControllerBase
{
    private readonly ReservationService _reservationService;
    private readonly ReservationQrService _qr;
    private readonly StationAccessService _access;

    public ReservationController(ReservationService reservationService, ReservationQrService qr, StationAccessService access)
    {
        _reservationService = reservationService;
        _qr = qr;
        _access = access;
    }

    [HttpGet]
    [Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
    public async Task<IActionResult> GetAll()
    {
        var list = User.IsInRole("Prosumer")
            ? await _reservationService.GetByProsumerAsync(User.FindFirstValue(ClaimTypes.NameIdentifier)!)
            : await _reservationService.GetAllAsync();
        var ids = await _access.StationIdsAsync(User);
        if (ids != null) list = list.Where(r => ids.Contains(r.StationId)).ToList();
        await _reservationService.PopulateDisplayNamesAsync(list);
        return Ok(list);
    }

    [HttpGet("{id}/qr")]
    public async Task<IActionResult> GetQr(string id)
    {
        if (!MongoDB.Bson.ObjectId.TryParse(id, out var parsed))
            return BadRequest(new { message = "Invalid reservation ID." });
        var reservation = await _reservationService.GetByIdAsync(parsed.ToString());
        if (reservation == null) return NotFound(new { message = "Reservation not found." });
        if (!CanAccess(reservation.ProsumerNIC)) return Forbid();
        if (!await _access.CanAccessAsync(User, reservation.StationId)) return Forbid();
        if (reservation.Status != "Approved")
            return Conflict(new { message = "Only approved reservations have a transaction QR." });
        return Ok(_qr.Issue(reservation));
    }

    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(string id)
    {
        var res = await _reservationService.GetByIdAsync(id);
        if (res == null) return NotFound(new { message = "Reservation not found." });
        if (!CanAccess(res.ProsumerNIC)) return Forbid();
        if (!await _access.CanAccessAsync(User, res.StationId)) return Forbid();
        await _reservationService.PopulateDisplayNamesAsync([res]);
        return Ok(res);
    }

    [HttpGet("prosumer/{nic}")]
    public async Task<IActionResult> GetByProsumer(string nic)
    {
        if (!CanAccess(nic)) return Forbid();
        var list = await _reservationService.GetByProsumerAsync(nic);
        var ids = await _access.StationIdsAsync(User);
        if (ids != null) list = list.Where(r => ids.Contains(r.StationId)).ToList();
        await _reservationService.PopulateDisplayNamesAsync(list);
        return Ok(list);
    }

    [HttpPost]
    public async Task<IActionResult> Create([FromBody] CreateReservationRequest request)
    {
        try
        {
            if (!CanAccess(request.ProsumerNIC)) return Forbid();
            if (!await _access.CanAccessSlotAsync(User, request.SlotId)) return Forbid();
            var created = await _reservationService.CreateAsync(request);
            await _reservationService.PopulateDisplayNamesAsync([created]);
            return CreatedAtAction(nameof(GetById), new { id = created.Id }, created);
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (InvalidOperationException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpPut("{id}")]
    public async Task<IActionResult> Update(string id, [FromBody] UpdateReservationRequest request)
    {
        try
        {
            var existing = await _reservationService.GetByIdAsync(id);
            if (existing == null) return NotFound();
            if (!CanAccess(existing.ProsumerNIC)) return Forbid();
            if (!await _access.CanAccessAsync(User, existing.StationId)) return Forbid();
            if (request.NewSlotId != null && !await _access.CanAccessSlotAsync(User, request.NewSlotId)) return Forbid();
            var updated = await _reservationService.UpdateAsync(id, request);
            await _reservationService.PopulateDisplayNamesAsync([updated]);
            return Ok(updated);
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (InvalidOperationException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpPatch("{id}/cancel")]
    public async Task<IActionResult> Cancel(string id, [FromQuery] string? reason)
    {
        try
        {
            var existing = await _reservationService.GetByIdAsync(id);
            if (existing == null) return NotFound();
            if (!CanAccess(existing.ProsumerNIC)) return Forbid();
            if (!await _access.CanAccessAsync(User, existing.StationId)) return Forbid();
            var cancelled = await _reservationService.CancelAsync(id, reason);
            await _reservationService.PopulateDisplayNamesAsync([cancelled]);
            return Ok(cancelled);
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (InvalidOperationException ex)
        {
            return BadRequest(new { message = ex.Message });
        }
    }

    [HttpPatch("{id}/status")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> UpdateStatus(string id, [FromBody] UpdateReservationStatusRequest request)
    {
        try
        {
            var existing = await _reservationService.GetByIdAsync(id);
            if (existing == null) return NotFound();
            if (!await _access.CanAccessAsync(User, existing.StationId)) return Forbid();
            var updated = await _reservationService.UpdateStatusAsync(id, request.Status);
            await _reservationService.PopulateDisplayNamesAsync([updated]);
            return Ok(updated);
        }
        catch (KeyNotFoundException ex)
        {
            return NotFound(new { message = ex.Message });
        }
        catch (InvalidOperationException ex)
        {
            return Conflict(new { message = ex.Message });
        }
    }
    // Staff may assist any prosumer; prosumers may only access their own bookings.
    private bool CanAccess(string nic) =>
        User.IsInRole("Backoffice") || User.IsInRole("Grid Operator") ||
        (User.IsInRole("Prosumer") && string.Equals(User.FindFirstValue(ClaimTypes.NameIdentifier)?.Trim(), nic?.Trim(), StringComparison.OrdinalIgnoreCase));
}
