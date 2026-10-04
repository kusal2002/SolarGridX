using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.Models;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[ApiController]
[Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
[Route("api/bookings")]
public class BookingsController(ReservationService reservations) : ControllerBase
{
    private async Task<List<EnergyReservation>> AccessibleBookings() => User.IsInRole("Prosumer")
        ? await reservations.GetByProsumerAsync(User.FindFirstValue(ClaimTypes.NameIdentifier)!)
        : await reservations.GetAllAsync();

    [HttpGet("{view:regex(^(current|pending|history|search)$)}")]
    public async Task<IActionResult> List(string view, string? search = null, string? status = null,
        string? stationId = null, DateTime? from = null, DateTime? to = null, int page = 1, int pageSize = 50)
    {
        if (page < 1 || page > 1000000 || pageSize < 1 || pageSize > 100 || from > to)
            return BadRequest(new { message = "Invalid page, page size or date range." });
        var list = (await AccessibleBookings()).Where(r => view switch
        {
            "current" => r.Status is "Approved" or "InProgress",
            "pending" => r.Status == "Pending",
            "history" => r.Status is "Completed" or "Cancelled",
            _ => true
        });
        if (!string.IsNullOrWhiteSpace(status)) list = list.Where(r => r.Status == status);
        if (!string.IsNullOrWhiteSpace(stationId)) list = list.Where(r => r.StationId == stationId);
        if (from != null) list = list.Where(r => r.ReservationDate >= from);
        if (to != null) list = list.Where(r => r.ReservationDate <= to);
        if (!string.IsNullOrWhiteSpace(search)) list = list.Where(r =>
            r.Id.Contains(search, StringComparison.OrdinalIgnoreCase) ||
            r.ProsumerNIC.Contains(search, StringComparison.OrdinalIgnoreCase) ||
            r.StationId.Contains(search, StringComparison.OrdinalIgnoreCase));
        var ordered = list.OrderByDescending(r => r.CreatedAt).ThenByDescending(r => r.Id).ToList();
        return Ok(new { items = ordered.Skip((page - 1) * pageSize).Take(pageSize), total = ordered.Count, page, pageSize });
    }

    [HttpGet("/api/dashboard/summary")]
    public async Task<IActionResult> Summary()
    {
        var list = await AccessibleBookings();
        var zone = TimeZoneInfo.FindSystemTimeZoneById(OperatingSystem.IsWindows() ? "Sri Lanka Standard Time" : "Asia/Colombo");
        var now = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, zone);
        return Ok(new
        {
            pending = list.Count(r => r.Status == "Pending"),
            current = list.Count(r => r.Status is "Approved" or "InProgress"),
            approvedFuture = list.Count(r => r.Status == "Approved" &&
                TimeZoneInfo.ConvertTimeFromUtc(DateTime.SpecifyKind(r.ReservationDate, DateTimeKind.Utc), zone).Date.Add(r.StartTime) > now),
            completed = list.Count(r => r.Status == "Completed"),
            cancelled = list.Count(r => r.Status == "Cancelled")
        });
    }
}
