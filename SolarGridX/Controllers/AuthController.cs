using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using System.Security.Claims;
using SolarGridX.DTOs;
using SolarGridX.Models;
using SolarGridX.Services;

namespace SolarGridX.Controllers
{
    [ApiController]
    [Route("api/auth")]
    public class AuthController : ControllerBase
    {
        private readonly AuthService _authService;

        public AuthController(AuthService authService)
        {
            _authService = authService;
        }

        [HttpPost("register")]
        [AllowAnonymous]
        public async Task<IActionResult> Register(
            [FromBody] RegisterUserDto dto)
        {
            if (!ModelState.IsValid)
            {
                return BadRequest(ModelState);
            }

            var result = await _authService.RegisterUserAsync(dto);

            if (result == null)
            {
                return Conflict(new
                {
                    message = "NIC or email is already registered."
                });
            }

            return CreatedAtAction(nameof(GetUser), new { nic = result.NIC }, result);
        }

        [HttpPost("login")]
        [AllowAnonymous]
        public async Task<IActionResult> Login(
    [FromBody] LoginUserDto dto)
        {
            if (!ModelState.IsValid)
            {
                return BadRequest(ModelState);
            }

            var result = await _authService.LoginUserAsync(dto);

            if (!result.Success)
            {
                return Unauthorized(new
                {
                    message = result.ErrorMessage
                });
            }

            return Ok(result.Data);
        }

        [HttpGet("users")]
        [Authorize(Roles = "Backoffice")]
        public async Task<IActionResult> GetUsers(
            [FromQuery] int page = 1,
            [FromQuery] int pageSize = 10,
            [FromQuery] string? search = null,
            [FromQuery] string? status = null,
            [FromQuery] string? role = null,
            [FromQuery] string sortBy = "createdAt",
            [FromQuery] string sortDirection = "desc")
        {
            if (page < 1 || pageSize is < 1 or > 100)
            {
                return BadRequest(new { message = "Page must be positive and pageSize must be between 1 and 100." });
            }

            if (!string.IsNullOrWhiteSpace(status)
                && (!Enum.TryParse<AccountStatus>(status, true, out var parsedStatus)
                    || !Enum.IsDefined(parsedStatus)))
            {
                return BadRequest(new { message = "Unsupported account status filter." });
            }

            return Ok(await _authService.GetUsersAsync(page, pageSize, search, status, role, sortBy, sortDirection));
        }

        // Resolve the resource URL returned when an account is created.
        [HttpGet("users/{nic}")]
        [Authorize]
        public async Task<IActionResult> GetUser(string nic)
        {
            if (User.FindFirstValue(ClaimTypes.NameIdentifier) != nic && !User.IsInRole("Backoffice"))
                return Forbid();
            var result = await _authService.GetUserByNicAsync(nic);
            return result == null ? NotFound() : Ok(result);
        }

        [HttpPatch("users/{nic}/status")]
        [Authorize(Roles = "Backoffice")]
        public async Task<IActionResult> UpdateStatus(string nic, [FromBody] string status)
        {
            if (!Enum.TryParse<AccountStatus>(status, true, out var requestedStatus)
                || !Enum.IsDefined(requestedStatus))
            {
                return BadRequest(new { message = "Unsupported account status." });
            }

            var authenticatedNic = User.FindFirstValue(ClaimTypes.NameIdentifier);
            var result = await _authService.UpdateAccountStatusAsync(nic, requestedStatus, authenticatedNic);
            if (result.User == null)
            {
                return result.Error == "User was not found."
                    ? NotFound(new { message = result.Error })
                    : Conflict(new { message = result.Error });
            }

            return Ok(result.User);
        }

        [HttpPost("staff")]
        [Authorize(Roles = "Backoffice")]
        public async Task<IActionResult> CreateStaffUser([FromBody] CreateStaffUserDto dto)
        {
            if (!ModelState.IsValid)
            {
                return BadRequest(ModelState);
            }

            var result = await _authService.CreateStaffUserAsync(dto);
            return result == null
                ? Conflict(new { message = "NIC or email is already registered." })
                : CreatedAtAction(nameof(GetUser), new { nic = result.NIC }, result);
        }

        [HttpPut("users/{nic}/profile")]
        [Authorize]
        public async Task<IActionResult> UpdateProfile(string nic, [FromBody] UpdateProfileDto dto)
        {
            var authenticatedNic = User.FindFirstValue(ClaimTypes.NameIdentifier);
            if (authenticatedNic != nic && !User.IsInRole("Backoffice"))
            {
                return Forbid();
            }

            if (!ModelState.IsValid)
            {
                return BadRequest(ModelState);
            }

            var result = await _authService.UpdateProfileAsync(nic, dto.Name, dto.Email);
            return result == null
                ? Conflict(new { message = "User was not found or email is already registered." })
                : Ok(result);
        }

        [HttpPost("users/{nic}/deactivation-request")]
        [Authorize(Roles = "Prosumer")]
        public async Task<IActionResult> RequestDeactivation(string nic)
        {
            if (User.FindFirstValue(ClaimTypes.NameIdentifier) != nic)
            {
                return Forbid();
            }

            var result = await _authService.UpdateAccountStatusAsync(nic, AccountStatus.DeactivationRequested, nic);
            if (result.User == null)
            {
                return result.Error == "User was not found."
                    ? NotFound(new { message = result.Error })
                    : Conflict(new { message = result.Error });
            }

            return Ok(result.User);
        }

        [HttpGet("me")]
        [Authorize]
        public async Task<IActionResult> GetMyProfile()
        {
            var nic = User.FindFirstValue(ClaimTypes.NameIdentifier);
            var result = await _authService.GetUserByNicAsync(nic ?? string.Empty);
            return result == null ? NotFound() : Ok(result);
        }
    }
}
