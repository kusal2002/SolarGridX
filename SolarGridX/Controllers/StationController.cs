using Microsoft.AspNetCore.Mvc;
using SolarGridX.DTOs.Stations;
using SolarGridX.Services;

namespace SolarGridX.Controllers;

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
    public async Task<IActionResult> Delete(string id)
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

    //Reactive Stations
    [HttpPatch("{id}/reactivate")]
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