using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.Models;
using SolarGridX.Services;
using MongoDB.Driver;

namespace SolarGridX.Controllers;

[ApiController]
[Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
[Route("api/bookings")]
public class BookingsController(ReservationService reservations, StationService stations, StationAccessService access, IMongoDatabase database) : ControllerBase
{
    private async Task<List<EnergyReservation>> AccessibleBookings()
    {
        var list = User.IsInRole("Prosumer")
            ? await reservations.GetByProsumerAsync(User.FindFirstValue(ClaimTypes.NameIdentifier)!)
            : await reservations.GetAllAsync();
        var ids = await access.StationIdsAsync(User);
        return ids == null ? list : list.Where(r => ids.Contains(r.StationId)).ToList();
    }

    [HttpGet("{view:regex(^(current|pending|completed|history|search)$)}")]
    public async Task<IActionResult> List(string view, string? search = null, string? status = null,
        string? stationId = null, DateTime? from = null, DateTime? to = null, int page = 1, int pageSize = 50)
    {
        if (page < 1 || page > 1000000 || pageSize < 1 || pageSize > 100 || from > to)
            return BadRequest(new { message = "Invalid page, page size or date range." });
        if (stationId != null && !await access.CanAccessAsync(User, stationId)) return Forbid();
        var list = (await AccessibleBookings()).Where(r => view switch
        {
            "current" => r.Status is "Approved" or "InProgress",
            "pending" => r.Status == "Pending",
            "completed" => r.Status == "Completed",
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
        var items = ordered.Skip((page - 1) * pageSize).Take(pageSize).ToList();
        var names = (await stations.GetByIdsAsync(items.Select(r => r.StationId).Distinct()))
            .ToDictionary(station => station.Id, station => station.StationName);
        foreach (var booking in items) booking.StationName = names.GetValueOrDefault(booking.StationId);
        await reservations.PopulateDisplayNamesAsync(items);
        return Ok(new { items, total = ordered.Count, page, pageSize });
    }

    [HttpGet("/api/dashboard/summary")]
    public async Task<IActionResult> Summary(string? stationId = null)
    {
        if (stationId != null && !await access.CanAccessAsync(User, stationId)) return Forbid();
        var list = await AccessibleBookings();
        if (!string.IsNullOrWhiteSpace(stationId)) list = list.Where(r => r.StationId == stationId).ToList();
        var ids = await access.StationIdsAsync(User);
        var stationList = await stations.GetAllIncludingInactiveAsync();
        if (ids != null) stationList = stationList.Where(s => ids.Contains(s.Id)).ToList();
        if (User.IsInRole("Prosumer")) stationList = stationList.Where(s => list.Any(r => r.StationId == s.Id)).ToList();
        if (!string.IsNullOrWhiteSpace(stationId)) stationList = stationList.Where(s => s.Id == stationId).ToList();
        var stationIds = stationList.Select(s => s.Id).ToList();
        var slots = await database.GetCollection<EnergyBookingSlot>("EnergyBookingSlots")
            .Find(Builders<EnergyBookingSlot>.Filter.In(s => s.StationId, stationIds)).ToListAsync();
        var transfers = await database.GetCollection<EnergyTransfer>("EnergyTransfers")
            .Find(Builders<EnergyTransfer>.Filter.In(t => t.ReservationId, list.Select(r => r.Id))).ToListAsync();
        var zone = TimeZoneInfo.FindSystemTimeZoneById(OperatingSystem.IsWindows() ? "Sri Lanka Standard Time" : "Asia/Colombo");
        var now = TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, zone);
        var upcomingSlots = slots.Where(s => s.IsActive && stationList.Any(station => station.Id == s.StationId && station.IsActive) &&
            TimeZoneInfo.ConvertTimeFromUtc(DateTime.SpecifyKind(s.SlotDate, DateTimeKind.Utc), zone).Date.Add(s.EndTime) > now).ToList();
        return Ok(new
        {
            pending = list.Count(r => r.Status == "Pending"),
            current = list.Count(r => r.Status is "Approved" or "InProgress"),
            approvedFuture = list.Count(r => r.Status == "Approved" &&
                TimeZoneInfo.ConvertTimeFromUtc(DateTime.SpecifyKind(r.ReservationDate, DateTimeKind.Utc), zone).Date.Add(r.StartTime) > now),
            completed = list.Count(r => r.Status == "Completed"),
            cancelled = list.Count(r => r.Status == "Cancelled"),
            stationCount = stationList.Count,
            activeStations = stationList.Count(s => s.IsActive),
            activeSlots = upcomingSlots.Count,
            availableEnergyKwh = upcomingSlots.Sum(s => s.AvailableEnergyKwh),
            activeTransfers = transfers.Count(t => t.Status == "InProgress"),
            completedTransfers = transfers.Count(t => t.Status == "Completed"),
            deliveredEnergyKwh = transfers.Where(t => t.Status == "Completed").Sum(t => t.TransferredEnergyKWh)
        });
    }
}
