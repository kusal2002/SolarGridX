// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: LoginResultDto.cs
// Description: Wrapper DTO representing the outcome of a user login attempt.
// ============================================================================

namespace SolarGridX.DTOs
{
    public class LoginResultDto
    {
        public bool Success { get; set; }
        public string? ErrorMessage { get; set; }
        public LoginResponseDto? Data { get; set; }

        public static LoginResultDto Succeeded(LoginResponseDto data)
        {
            // Create a successful login result containing user and token data
            return new LoginResultDto
            {
                Success = true,
                Data = data
            };
        }

        public static LoginResultDto Failed(string errorMessage)
        {
            // Create a failed login result with an explanatory error message
            return new LoginResultDto
            {
                Success = false,
                ErrorMessage = errorMessage
            };
        }
    }
}
