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
    public async Task<IActionResult> GetById(String id)
    {
        var slot = await _slotService.GetByIdAsync(id);

        if (slot is null)
        {
            return NotFound((new
            {
                MessageProcessingHandler = "Slot Not found"
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

    [HttpPost]
    public async Task<IActionResult> Create(
        CreateSlotRequest request)
    {
        var slot = await _slotService.CreateAsync(request);

        if (slot is null)
        {
            return NotFound(new
            {
                message = "Active station not found"
            });
        }

        return CreatedAtAction(
            nameof(GetById),
            new { id = slot.Id },
            slot
        );
    }
}