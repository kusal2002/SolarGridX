using BCrypt.Net;
using MongoDB.Driver;
using SolarGridX.DTOs;
using SolarGridX.Models;

namespace SolarGridX.Services
{
    public class AuthService
    {
        public async Task<LoginResponseDto?> LoginUserAsync(
    LoginUserDto dto)
        {
            var email = dto.Email.Trim().ToLowerInvariant();

            var user = await _users
                .Find(x => x.Email == email)
                .FirstOrDefaultAsync();

            if (user == null)
            {
                return null;
            }

            var passwordValid = BCrypt.Net.BCrypt.Verify(
                dto.Password,
                user.PasswordHash
            );

            if (!passwordValid)
            {
                return null;
            }

            if (user.AccountStatus != "Active")
            {
                return null;
            }

            return new LoginResponseDto
            {
                NIC = user.NIC,
                Name = user.Name,
                Email = user.Email,
                Role = user.Role,
                AccountStatus = user.AccountStatus
            };
        }
        private readonly IMongoCollection<User> _users;

        public AuthService(IMongoDatabase database)
        {
            _users = database.GetCollection<User>("Users");
        }

        public async Task<UserResponseDto?> RegisterUserAsync(
            RegisterUserDto dto)
        {
            var nic = dto.NIC.Trim();
            var email = dto.Email.Trim().ToLowerInvariant();

            // Check whether NIC already exists
            var existingNIC = await _users
                .Find(x => x.NIC == nic)
                .FirstOrDefaultAsync();

            if (existingNIC != null)
            {
                return null;
            }

            // Check whether email already exists
            var existingEmail = await _users
                .Find(x => x.Email == email)
                .FirstOrDefaultAsync();

            if (existingEmail != null)
            {
                return null;
            }

            // Hash password before saving
            var passwordHash = BCrypt.Net.BCrypt.HashPassword(
                dto.Password
            );

            var user = new User
            {
                NIC = nic,
                Name = dto.Name.Trim(),
                Email = email,
                PasswordHash = passwordHash,
                Role = "Prosumer",
                AccountStatus = "Active",
                CreatedAt = DateTime.UtcNow
            };

            await _users.InsertOneAsync(user);

            return new UserResponseDto
            {
                NIC = user.NIC,
                Name = user.Name,
                Email = user.Email,
                Role = user.Role,
                AccountStatus = user.AccountStatus,
                CreatedAt = user.CreatedAt
            };
        }
    }
}