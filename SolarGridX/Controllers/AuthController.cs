using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Authorization;
using System.Security.Claims;
using SolarGridX.DTOs;
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

            return Created(
                $"/api/auth/users/{result.NIC}",
                result
            );
        }

        [HttpPost("login")]
        public async Task<IActionResult> Login(
    [FromBody] LoginUserDto dto)
        {
            if (!ModelState.IsValid)
            {
                return BadRequest(ModelState);
            }

            var result = await _authService.LoginUserAsync(dto);

            if (result == null)
            {
                return Unauthorized(new
                {
                    message = "Invalid email or password."
                });
            }

            return Ok(result);
        }

        [HttpGet("users")]
        [Authorize(Roles = "Backoffice")]
        public async Task<IActionResult> GetUsers([FromQuery] string? status, [FromQuery] string? role)
        {
            return Ok(await _authService.GetUsersAsync(status, role));
        }

        [HttpPatch("users/{nic}/status")]
        [Authorize(Roles = "Backoffice")]
        public async Task<IActionResult> UpdateStatus(string nic, [FromBody] string status)
        {
            var allowedStatuses = new[] { "Active", "Inactive", "DeactivationRequested" };
            if (!allowedStatuses.Contains(status))
            {
                return BadRequest(new { message = "Unsupported account status." });
            }

            var result = await _authService.UpdateAccountStatusAsync(nic, status);
            return result == null ? NotFound() : Ok(result);
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
                : Created($"/api/auth/users/{result.NIC}", result);
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

            var result = await _authService.UpdateAccountStatusAsync(nic, "DeactivationRequested");
            return result == null ? NotFound() : Ok(result);
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