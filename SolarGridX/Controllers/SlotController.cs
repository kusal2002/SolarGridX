using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs.Slots;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[ApiController]
[Route("api/slots")]
public class SlotController : ControllerBase
{
    private readonly SlotService _slotService;

    public SlotController(SlotService slotService)
    {
        _slotService = slotService;
    }

    //Get all active slots
    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var slots = await _slotService.GetAllAsync();

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
        return Ok(slots);
    }

    //Create a station slot
    [HttpPost]
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