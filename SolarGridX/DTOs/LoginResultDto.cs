namespace SolarGridX.DTOs
{
    public class LoginResultDto
    {
        public bool Success { get; set; }
        public string? ErrorMessage { get; set; }
        public LoginResponseDto? Data { get; set; }

        public static LoginResultDto Succeeded(LoginResponseDto data) => new()
        {
            Success = true,
            Data = data
        };

        public static LoginResultDto Failed(string errorMessage) => new()
        {
            Success = false,
            ErrorMessage = errorMessage
        };
    }
}
