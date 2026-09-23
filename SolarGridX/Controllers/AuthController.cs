using Microsoft.AspNetCore.Mvc;
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
    }
}