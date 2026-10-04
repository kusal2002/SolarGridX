using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs.Slots;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
[ApiController]
[Route("api/slots")]
public class SlotController : ControllerBase
{
    private readonly SlotService _slotService;

    public SlotController(SlotService slotService)
    {
        _slotService = slotService;
    }

    private DateTime GetSriLankaTime()
    {
        try
        {
            var tz = TimeZoneInfo.FindSystemTimeZoneById("Asia/Colombo");
            return TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);
        }
        catch (TimeZoneNotFoundException)
        {
            var tz = TimeZoneInfo.FindSystemTimeZoneById("Sri Lanka Standard Time");
            return TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, tz);
        }
    }

    //Get all active slots
    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var slots = await _slotService.GetAllAsync();

        if (User.IsInRole("Prosumer"))
        {
            var now = GetSriLankaTime();
            slots = slots.Where(s => s.SlotDate.Date.Add(s.EndTime) > now && s.AvailableEnergyKwh > 0).ToList();
        }

        return Ok(slots);
    }

    // Get all slots including inactive
    [HttpGet("all")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> GetAllIncludingInactive()
    {
        var slots = await _slotService.GetAllIncludingInactiveAsync();
        return Ok(slots);
    }

    //Get slot by id
    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(string id)
    {
        var slot = await _slotService.GetByIdAsync(id);

        if (slot is null)
        {
            return NotFound((new
            {
                message = "Slot Not found"
            }));
        }
        return Ok(slot);
    }

    //Get slot by station id
    [HttpGet("station/{stationId}")]
    public async Task<IActionResult> GetByStationId(
    string stationId)
    {
        var slots = await _slotService.GetByStationIdAsync(stationId);

        if (User.IsInRole("Prosumer"))
        {
            var now = GetSriLankaTime();
            slots = slots.Where(s => s.SlotDate.Date.Add(s.EndTime) > now && s.AvailableEnergyKwh > 0).ToList();
        }

        return Ok(slots);
    }

    //Get all slots by station id (including inactive)
    [HttpGet("station/{stationId}/all")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> GetByStationIdIncludingInactive(
    string stationId)
    {
        var slots = await _slotService.GetByStationIdIncludingInactiveAsync(stationId);
        return Ok(slots);
    }

    //Create a station slot
    [HttpPost]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> Create(CreateSlotRequest request)
    {
        try
        {
            var slot = await _slotService.CreateAsync(request);

            if (slot is null)
            {
                return NotFound(new
                {
                    message = "Active station not found."
                });
            }

            return CreatedAtAction(
                nameof(GetById),
                new { id = slot.Id },
                slot
            );
        }
        catch (ArgumentException ex)
        {
            return BadRequest(new
            {
                message = ex.Message
            });
        }
        catch (InvalidOperationException ex)
        {
            return Conflict(new
            {
                message = ex.Message
            });
        }
    }

    //Update slots

    [HttpPut("{id}")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> Update(
    string id,
    UpdateSlotRequest request)
    {
        try
        {
            var slot = await _slotService.UpdateAsync(id, request);

            if (slot is null)
            {
                return NotFound(new
                {
                    message = "Active slot not found."
                });
            }

            return Ok(slot);
        }
        catch (ArgumentException ex)
        {
            return BadRequest(new
            {
                message = ex.Message
            });
        }
        catch (InvalidOperationException ex)
        {
            return Conflict(new
            {
                message = ex.Message
            });
        }
    }

    // Deactivate Slot
    [HttpPatch("{id}/deactivate")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> Deactivate(string id)
    {
        var slot = await _slotService.DeactivateAsync(id);

        if (slot is null)
        {
            return NotFound(new
            {
                message = "Active slot not found."
            });
        }

        return Ok(new
        {
            message = "Slot deactivated successfully.",
            slot
        });
    }

    // Reactivate Slot
    [HttpPatch("{id}/reactivate")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> Reactivate(string id)
    {
        try
        {
            var slot = await _slotService.ReactivateAsync(id);

            if (slot is null)
            {
                return NotFound(new
                {
                    message = "Inactive slot not found."
                });
            }

            return Ok(new
            {
                message = "Slot reactivated successfully.",
                slot
            });
        }
        catch (InvalidOperationException ex)
        {
            return Conflict(new
            {
                message = ex.Message
            });
        }
    }

    //Delete slots
    [HttpDelete("{id}")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Delete(string id)
    {
        var deleted = await _slotService.DeleteAsync(id);

        if (!deleted)
        {
            return NotFound(new
            {
                message = "Slot not found."
            });
        }

        return Ok(new
        {
            message = "Slot deleted successfully."
        });
    }



}