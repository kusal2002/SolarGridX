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
    private readonly StationAccessService _access;

    public StationController(StationService stationService, StationAccessService access)
    {
        _stationService = stationService;
        _access = access;
    }

    //Get all stations
    [HttpGet]
    public async Task<IActionResult> GetAll()
    {
        var stations = await _stationService.GetAllAsync();
        var ids = await _access.StationIdsAsync(User);
        if (ids != null) stations = stations.Where(s => ids.Contains(s.Id)).ToList();

        return Ok(stations);
    }

    // Get all stations including inactive stations
    [HttpGet("all")]
    [Authorize(Roles = "Backoffice,Grid Operator")]
    public async Task<IActionResult> GetAllIncludingInactive()
    {
        var stations = await _stationService
            .GetAllIncludingInactiveAsync();
        var ids = await _access.StationIdsAsync(User);
        if (ids != null) stations = stations.Where(s => ids.Contains(s.Id)).ToList();

        return Ok(stations);
    }

    //Get station by id
    [HttpGet("{id}")]
    public async Task<IActionResult> GetById(string id)
    {
        if (!await _access.CanAccessAsync(User, id)) return Forbid();
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

    [HttpGet("operators")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Operators() => Ok((await _stationService.GetOperatorsAsync()).Select(u => new { u.NIC, u.Name }));

    public record Assignment(string? OperatorNIC, List<string>? OperatorNICs = null);

    [HttpPatch("{id}/operator")]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> AssignOperator(string id, Assignment request)
    {
        try
        {
            var station = await _stationService.AssignOperatorsAsync(id, request.OperatorNICs ?? (string.IsNullOrWhiteSpace(request.OperatorNIC) ? [] : [request.OperatorNIC]));
            return station == null ? NotFound(new { message = "Station not found." }) : Ok(station);
        }
        catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
    }

    //Create station
    [HttpPost]
    [Authorize(Roles = "Backoffice")]
    public async Task<IActionResult> Create(
        CreateStationRequest request)
    {
        try
        {
        var station = await _stationService.CreateAsync(request);

        return CreatedAtAction(
            nameof(GetById),
            new { id = station.Id },
            station
        );
        }
        catch (ArgumentException ex) { return BadRequest(new { message = ex.Message }); }
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
