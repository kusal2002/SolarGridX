using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs.Stations;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

[Authorize(Roles = "Backoffice,Grid Operator,Prosumer")]
[ApiController]
[Route("api/stations")]

public class StationController : ControllerBase
{
    private readonly StationService _stationService;

    public StationController(StationService stationService)
    {
        _stationService = stationService;
    }

    //Get all stations
    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var stations = await _stationService.GetAllAsync();

        return Ok(stations);
    }

    // Get all stations including inactive stations
    [HttpGet("all")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> GetAllIncludingInactive()
    {
        var stations = await _stationService
            .GetAllIncludingInactiveAsync();

        return Ok(stations);
    }

    //Get station by id
    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(string id)
    {
        var station = await _stationService.GetByIdAsync(id);

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        return Ok(station);
    }

    //Create station
    [HttpPost]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Create(
        CreateStationRequest request)
    {
        var station = await _stationService.CreateAsync(request);

        return CreatedAtAction(
            nameof(GetById),
            new { id = station.Id },
            station
        );
    }

    //Update station
    [HttpPut("{id}")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Update(
    string id,
    UpdateStationRequest request)
    {
        var station = await _stationService.UpdateAsync(
            id,
            request
        );

        if (station is null)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        return Ok(station);
    }

    //Delete or Deactive station
    [HttpDelete("{id}")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Delete(string id)
    {
        try
        {
            var deactivated = await _stationService.DeactivateAsync(id);

            if (!deactivated)
            {
                return NotFound(new
                {
                    message = "Station not found."
                });
            }

            return Ok(new
            {
                message = "Station deactivated successfully."
            });
        }
        catch (InvalidOperationException ex)
        {
            return BadRequest(new
            {
                message = ex.Message
            });
        }
    }

    //Reactive Stations
    [HttpPatch("{id}/reactivate")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Reactivate(string id)
    {
        var reactivated = await _stationService.ReactivateAsync(id);

        if (!reactivated)
        {
            return NotFound(new
            {
                message = "Station not found."
            });
        }

        return Ok(new
        {
            message = "Station reactivated successfully."
        });
    }


}