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
    private readonly StationAccessService _access;

    public SlotController(SlotService slotService, StationAccessService access)
    {
        _slotService = slotService;
        _access = access;
    }

    //Get all active slots
    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var slots = await _slotService.GetAllAsync();
        var ids = await _access.StationIdsAsync(User);
        if (ids != null) slots = slots.Where(s => ids.Contains(s.StationId)).ToList();

        return Ok(slots);
    }

    // Get all slots including inactive
    [HttpGet("all")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> GetAllIncludingInactive()
    {
        var slots = await _slotService.GetAllIncludingInactiveAsync();
        var ids = await _access.StationIdsAsync(User);
        if (ids != null) slots = slots.Where(s => ids.Contains(s.StationId)).ToList();
        return Ok(slots);
    }

    //Get slot by id
    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(string id)
    {
        var slot = await _slotService.GetByIdAsync(id);
        if (slot != null && !await _access.CanAccessAsync(User, slot.StationId)) return Forbid();

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
        if (!await _access.CanAccessAsync(User, stationId)) return Forbid();
        var slots = await _slotService.GetByStationIdAsync(stationId);
        return Ok(slots);
    }

    //Get all slots by station id (including inactive)
    [HttpGet("station/{stationId}/all")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> GetByStationIdIncludingInactive(
    string stationId)
    {
        if (!await _access.CanAccessAsync(User, stationId)) return Forbid();
        var slots = await _slotService.GetByStationIdIncludingInactiveAsync(stationId);
        return Ok(slots);
    }

    //Create a station slot
    [HttpPost]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> Create(CreateSlotRequest request)
    {
        if (!await _access.CanAccessAsync(User, request.StationId)) return Forbid();
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
        if (!await _access.CanAccessSlotAsync(User, id)) return Forbid();
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
        if (!await _access.CanAccessSlotAsync(User, id)) return Forbid();
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
        if (!await _access.CanAccessSlotAsync(User, id)) return Forbid();
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
