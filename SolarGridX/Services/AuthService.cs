using BCrypt.Net;
using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using Microsoft.IdentityModel.Tokens;
using MongoDB.Bson;
using MongoDB.Driver;
using SolarGridX.DTOs;
using SolarGridX.Models;
using System.Text.RegularExpressions;

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

            if (user.AccountStatus != AccountStatus.Active)
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
                AccountStatus = user.AccountStatus.ToString(),
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
                AccountStatus = AccountStatus.Pending,
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
                AccountStatus = user.AccountStatus.ToString(),
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
                AccountStatus = AccountStatus.Active,
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
                && user.AccountStatus == AccountStatus.Active
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

        public async Task<PagedUserResponseDto> GetUsersAsync(
            int page,
            int pageSize,
            string? search,
            string? status,
            string? role,
            string sortBy,
            string sortDirection)
        {
            var filter = Builders<User>.Filter.Empty;

            if (!string.IsNullOrWhiteSpace(status))
            {
                if (Enum.TryParse<AccountStatus>(status.Trim(), true, out var parsedStatus))
                {
                    filter &= Builders<User>.Filter.Eq(x => x.AccountStatus, parsedStatus);
                }
            }

            if (!string.IsNullOrWhiteSpace(role))
            {
                filter &= Builders<User>.Filter.Eq(x => x.Role, role.Trim());
            }

            if (!string.IsNullOrWhiteSpace(search))
            {
                var searchRegex = new BsonRegularExpression(Regex.Escape(search.Trim()), "i");
                filter &= Builders<User>.Filter.Or(
                    Builders<User>.Filter.Regex(x => x.NIC, searchRegex),
                    Builders<User>.Filter.Regex(x => x.Name, searchRegex),
                    Builders<User>.Filter.Regex(x => x.Email, searchRegex));
            }

            var sort = sortBy.Trim().ToLowerInvariant() switch
            {
                "nic" => sortDirection.Equals("asc", StringComparison.OrdinalIgnoreCase)
                    ? Builders<User>.Sort.Ascending(x => x.NIC)
                    : Builders<User>.Sort.Descending(x => x.NIC),
                "name" => sortDirection.Equals("asc", StringComparison.OrdinalIgnoreCase)
                    ? Builders<User>.Sort.Ascending(x => x.Name)
                    : Builders<User>.Sort.Descending(x => x.Name),
                "email" => sortDirection.Equals("asc", StringComparison.OrdinalIgnoreCase)
                    ? Builders<User>.Sort.Ascending(x => x.Email)
                    : Builders<User>.Sort.Descending(x => x.Email),
                "role" => sortDirection.Equals("asc", StringComparison.OrdinalIgnoreCase)
                    ? Builders<User>.Sort.Ascending(x => x.Role)
                    : Builders<User>.Sort.Descending(x => x.Role),
                "status" => sortDirection.Equals("asc", StringComparison.OrdinalIgnoreCase)
                    ? Builders<User>.Sort.Ascending(x => x.AccountStatus)
                    : Builders<User>.Sort.Descending(x => x.AccountStatus),
                _ => sortDirection.Equals("asc", StringComparison.OrdinalIgnoreCase)
                    ? Builders<User>.Sort.Ascending(x => x.CreatedAt)
                    : Builders<User>.Sort.Descending(x => x.CreatedAt)
            };

            var totalCount = await _users.CountDocumentsAsync(filter);
            var users = await _users.Find(filter)
                .Sort(sort)
                .Skip((page - 1) * pageSize)
                .Limit(pageSize)
                .ToListAsync();

            return new PagedUserResponseDto
            {
                Items = users.Select(MapUser).ToList(),
                Page = page,
                PageSize = pageSize,
                TotalCount = totalCount,
                TotalPages = (int)Math.Ceiling(totalCount / (double)pageSize)
            };
        }

        public async Task<(UserResponseDto? User, string? Error)> UpdateAccountStatusAsync(
            string nic,
            AccountStatus nextStatus,
            string? actorNic = null)
        {
            var currentUser = await _users.Find(x => x.NIC == nic).FirstOrDefaultAsync();
            if (currentUser == null)
            {
                return (null, "User was not found.");
            }

            if (string.Equals(currentUser.NIC, actorNic, StringComparison.OrdinalIgnoreCase)
                && currentUser.Role == "Backoffice")
            {
                return (null, "A Backoffice user cannot deactivate their own account.");
            }

            if (!CanTransition(currentUser.AccountStatus, nextStatus))
            {
                return (null, $"Cannot change account status from {currentUser.AccountStatus} to {nextStatus}.");
            }

            var update = Builders<User>.Update
                .Set(x => x.AccountStatus, nextStatus)
                .Set(x => x.DeactivationRequestedAt, nextStatus == AccountStatus.DeactivationRequested ? DateTime.UtcNow : null)
                .Set(x => x.SecurityStamp, Guid.NewGuid().ToString("N"));

            var user = await _users.FindOneAndUpdateAsync(
                x => x.NIC == nic,
                update,
                new FindOneAndUpdateOptions<User> { ReturnDocument = ReturnDocument.After });

            return user == null ? (null, "User was not found.") : (MapUser(user), null);
        }

        private static bool CanTransition(AccountStatus currentStatus, AccountStatus nextStatus)
        {
            if (currentStatus == nextStatus)
            {
                return true;
            }

            return currentStatus switch
            {
                AccountStatus.Pending => nextStatus == AccountStatus.Active,
                AccountStatus.Active => nextStatus == AccountStatus.Inactive,
                AccountStatus.Inactive => nextStatus == AccountStatus.Active,
                AccountStatus.DeactivationRequested => nextStatus is AccountStatus.Active or AccountStatus.Inactive,
                _ => false
            };
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
                AccountStatus = user.AccountStatus.ToString(),
                CreatedAt = user.CreatedAt,
                DeactivationRequestedAt = user.DeactivationRequestedAt
            };
        }
    }
}