namespace SolarGridX.DTOs
{
    public class LoginResponseDto
    {
        public string NIC { get; set; } = string.Empty;

        public string Name { get; set; } = string.Empty;

        public string Email { get; set; } = string.Empty;

        public string Role { get; set; } = string.Empty;

        public string AccountStatus { get; set; } = string.Empty;

        public string Token { get; set; } = string.Empty;
    }
}