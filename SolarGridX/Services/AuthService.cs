using BCrypt.Net;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.IdentityModel.Tokens;
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

            if (string.IsNullOrWhiteSpace(user.SecurityStamp))
            {
                user.SecurityStamp = Guid.NewGuid().ToString("N");
                await _users.ReplaceOneAsync(x => x.NIC == user.NIC, user);
            }

            return new LoginResponseDto
            {
                NIC = user.NIC,
                Name = user.Name,
                Email = user.Email,
                Role = user.Role,
                AccountStatus = user.AccountStatus,
                Token = CreateToken(user)
            };
        }
        private readonly IMongoCollection<User> _users;
        private readonly IConfiguration _configuration;

        public AuthService(IMongoDatabase database, IConfiguration configuration)
        {
            _users = database.GetCollection<User>("Users");
            _configuration = configuration;
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
                AccountStatus = "Pending",
                SecurityStamp = Guid.NewGuid().ToString("N"),
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
                CreatedAt = user.CreatedAt,
                DeactivationRequestedAt = user.DeactivationRequestedAt
            };
        }

        public async Task<UserResponseDto?> GetUserByNicAsync(string nic)
        {
            var user = await _users.Find(x => x.NIC == nic).FirstOrDefaultAsync();
            return user == null ? null : MapUser(user);
        }

        private string CreateToken(User user)
        {
            var jwt = _configuration.GetSection("Jwt");
            var key = jwt["Key"] ?? throw new InvalidOperationException("JWT key is not configured.");
            var securityKey = new SymmetricSecurityKey(System.Text.Encoding.UTF8.GetBytes(key));
            var credentials = new SigningCredentials(securityKey, SecurityAlgorithms.HmacSha256);
            var claims = new[]
            {
                new Claim(ClaimTypes.NameIdentifier, user.NIC),
                new Claim(ClaimTypes.Name, user.Name),
                new Claim(ClaimTypes.Email, user.Email),
                    new Claim(ClaimTypes.Role, user.Role),
                    new Claim("security_stamp", user.SecurityStamp)
            };

            var token = new JwtSecurityToken(
                issuer: jwt["Issuer"],
                audience: jwt["Audience"],
                claims: claims,
                expires: DateTime.UtcNow.AddHours(8),
                signingCredentials: credentials);

            return new JwtSecurityTokenHandler().WriteToken(token);
        }

        public async Task<UserResponseDto?> CreateStaffUserAsync(CreateStaffUserDto dto)
        {
            var nic = dto.NIC.Trim();
            var email = dto.Email.Trim().ToLowerInvariant();
            var duplicate = await _users.Find(x => x.NIC == nic || x.Email == email).AnyAsync();

            if (duplicate)
            {
                return null;
            }

            var user = new User
            {
                NIC = nic,
                Name = dto.Name.Trim(),
                Email = email,
                PasswordHash = BCrypt.Net.BCrypt.HashPassword(dto.Password),
                Role = dto.Role,
                AccountStatus = "Active",
                SecurityStamp = Guid.NewGuid().ToString("N"),
                CreatedAt = DateTime.UtcNow
            };

            await _users.InsertOneAsync(user);
            return MapUser(user);
        }

        public async Task<bool> IsTokenActiveAsync(string nic, string securityStamp)
        {
            var user = await _users.Find(x => x.NIC == nic).FirstOrDefaultAsync();
            return user != null
                && user.AccountStatus == "Active"
                && user.SecurityStamp == securityStamp;
        }

        public async Task EnsureBootstrapBackofficeAsync(string? nic, string? name, string? email, string? password)
        {
            if (string.IsNullOrWhiteSpace(nic) || string.IsNullOrWhiteSpace(name)
                || string.IsNullOrWhiteSpace(email) || string.IsNullOrWhiteSpace(password))
            {
                return;
            }

            var normalizedEmail = email.Trim().ToLowerInvariant();
            var exists = await _users.Find(x => x.NIC == nic.Trim() || x.Email == normalizedEmail).AnyAsync();
            if (exists)
            {
                return;
            }

            await CreateStaffUserAsync(new CreateStaffUserDto
            {
                NIC = nic,
                Name = name,
                Email = normalizedEmail,
                Password = password,
                Role = "Backoffice"
            });
        }

        public async Task<List<UserResponseDto>> GetUsersAsync(string? status, string? role)
        {
            var filter = Builders<User>.Filter.Empty;

            if (!string.IsNullOrWhiteSpace(status))
            {
                filter &= Builders<User>.Filter.Eq(x => x.AccountStatus, status.Trim());
            }

            if (!string.IsNullOrWhiteSpace(role))
            {
                filter &= Builders<User>.Filter.Eq(x => x.Role, role.Trim());
            }

            var users = await _users.Find(filter).SortByDescending(x => x.CreatedAt).ToListAsync();
            return users.Select(MapUser).ToList();
        }

        public async Task<UserResponseDto?> UpdateAccountStatusAsync(string nic, string status)
        {
            var update = Builders<User>.Update
                .Set(x => x.AccountStatus, status)
                .Set(x => x.DeactivationRequestedAt, status == "DeactivationRequested" ? DateTime.UtcNow : null)
                .Set(x => x.SecurityStamp, Guid.NewGuid().ToString("N"));

            var user = await _users.FindOneAndUpdateAsync(
                x => x.NIC == nic,
                update,
                new FindOneAndUpdateOptions<User> { ReturnDocument = ReturnDocument.After });

            return user == null ? null : MapUser(user);
        }

        public async Task<UserResponseDto?> UpdateProfileAsync(string nic, string name, string email)
        {
            var normalizedEmail = email.Trim().ToLowerInvariant();
            var duplicateEmail = await _users.Find(x => x.Email == normalizedEmail && x.NIC != nic).AnyAsync();

            if (duplicateEmail)
            {
                return null;
            }

            var update = Builders<User>.Update
                .Set(x => x.Name, name.Trim())
                .Set(x => x.Email, normalizedEmail);

            var user = await _users.FindOneAndUpdateAsync(
                x => x.NIC == nic,
                update,
                new FindOneAndUpdateOptions<User> { ReturnDocument = ReturnDocument.After });

            return user == null ? null : MapUser(user);
        }

        private static UserResponseDto MapUser(User user)
        {
            return new UserResponseDto
            {
                NIC = user.NIC,
                Name = user.Name,
                Email = user.Email,
                Role = user.Role,
                AccountStatus = user.AccountStatus,
                CreatedAt = user.CreatedAt,
                DeactivationRequestedAt = user.DeactivationRequestedAt
            };
        }
    }
}