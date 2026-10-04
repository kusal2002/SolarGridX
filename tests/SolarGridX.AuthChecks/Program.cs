// Authorization regression checks; uses ASP.NET's policy evaluator without a database.
using System.Reflection;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Routing;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;
using MongoDB.Driver;
using SolarGridX.Controllers;
using SolarGridX.DTOs;
using SolarGridX.DTOs.Reservations;
using SolarGridX.Models;
using SolarGridX.Services;

var services = new ServiceCollection().AddLogging().AddAuthorization().BuildServiceProvider();
var provider = services.GetRequiredService<IAuthorizationPolicyProvider>();
var authorization = services.GetRequiredService<IAuthorizationService>();
var checks = 0;

// Evaluate combined controller/action policies exactly as ASP.NET combines role requirements.
async Task CheckPolicy(Type controller, string action, params string[] allowedRoles)
{
    var method = controller.GetMethod(action)!;
    var metadata = controller.GetCustomAttributes<AuthorizeAttribute>()
        .Concat(method.GetCustomAttributes<AuthorizeAttribute>()).ToArray();
    var policy = await AuthorizationPolicy.CombineAsync(provider, metadata)
        ?? throw new Exception($"Missing authorization: {controller.Name}.{action}");
    foreach (var role in new[] { "", "Backoffice", "Grid Operator", "Prosumer", "Unknown" })
    {
        var principal = Principal(role);
        var result = await authorization.AuthorizeAsync(principal, null, policy);
        Expect(result.Succeeded == allowedRoles.Contains(role), $"{controller.Name}.{action}: {role}");
    }
}

// Build an authenticated identity only when a role is supplied.
ClaimsPrincipal Principal(string role) => new(new ClaimsIdentity(
    new[] { new Claim(ClaimTypes.NameIdentifier, "owner"), new Claim(ClaimTypes.Role, role) },
    role.Length == 0 ? null : "RegressionTest"));

// Fail fast so the process exit code can be used by CI.
void Expect(bool condition, string description)
{
    if (!condition) throw new Exception($"FAILED: {description}");
    checks++;
}

foreach (var action in new[] { "GetUsers", "CreateStaffUser", "UpdateStatus" })
    await CheckPolicy(typeof(AuthController), action, "Backoffice");
await CheckPolicy(typeof(AuthController), "RequestDeactivation", "Prosumer");
foreach (var action in new[] { "Create", "Update", "Delete", "Reactivate", "GetAllIncludingInactive" })
    await CheckPolicy(typeof(StationController), action, "Backoffice");
foreach (var action in new[] { "GetAll", "GetById" })
    await CheckPolicy(typeof(StationController), action, "Backoffice", "Grid Operator", "Prosumer");
foreach (var action in new[] { "Create", "Update", "Deactivate", "Reactivate" })
    await CheckPolicy(typeof(SlotController), action, "Backoffice", "Grid Operator");
await CheckPolicy(typeof(SlotController), "Delete", "Backoffice");
foreach (var action in new[] { "GetAll", "UpdateStatus" })
    await CheckPolicy(typeof(ReservationController), action, "Backoffice", "Grid Operator");
foreach (var action in new[] { "GetById", "GetByProsumer", "Create", "Update", "Cancel" })
    await CheckPolicy(typeof(ReservationController), action, "Backoffice", "Grid Operator", "Prosumer");
foreach (var action in new[] { "GetAll", "GetById", "GetHistory", "Create", "Start", "Progress", "Complete", "Cancel", "Fail" })
    await CheckPolicy(typeof(EnergyTransfersController), action, "Backoffice", "Grid Operator");
await CheckPolicy(typeof(DatabaseTestController), "TestConnection", "Backoffice");

// Protect new controller actions from accidentally becoming public.
foreach (var type in typeof(AuthController).Assembly.GetTypes().Where(t => t.IsSubclassOf(typeof(ControllerBase)) || t.IsSubclassOf(typeof(Controller))))
foreach (var method in type.GetMethods().Where(m => m.GetCustomAttributes<HttpMethodAttribute>().Any()))
{
    var anonymous = method.GetCustomAttribute<AllowAnonymousAttribute>() != null;
    var expectedPublic = type == typeof(AuthController) && (method.Name == "Login" || method.Name == "Register");
    Expect(anonymous == expectedPublic, $"Public allowlist: {type.Name}.{method.Name}");
    if (!anonymous)
        Expect(type.GetCustomAttributes<AuthorizeAttribute>().Any() || method.GetCustomAttributes<AuthorizeAttribute>().Any(),
            $"Explicit endpoint policy: {type.Name}.{method.Name}");
}

// These denial paths must stop before touching any database service.
var context = new ControllerContext { HttpContext = new DefaultHttpContext { User = Principal("Prosumer") } };
var auth = new AuthController(null!) { ControllerContext = context };
Expect(await auth.GetUser("other") is ForbidResult, "Cannot read another account");
Expect(await auth.UpdateProfile("other", new UpdateProfileDto { Name = "Other", Email = "other@example.com" }) is ForbidResult, "Cannot edit another account");
Expect(await auth.RequestDeactivation("other") is ForbidResult, "Cannot deactivate another account");
var reservations = new ReservationController(null!) { ControllerContext = context };
Expect(await reservations.GetByProsumer("other") is ForbidResult, "Cannot list another prosumer's bookings");
Expect(await reservations.Create(new CreateReservationRequest { ProsumerNIC = "other" }) is ForbidResult, "Cannot book using another NIC");

Console.WriteLine($"Passed {checks} authorization regression checks. Database workflows are not exercised by this runner.");
if (!args.Contains("--integration")) return;

var configuration = new ConfigurationBuilder()
    .SetBasePath(Path.GetFullPath("SolarGridX"))
    .AddJsonFile("appsettings.json")
    .AddJsonFile("appsettings.Development.json", optional: true)
    .AddUserSecrets("8516e0c0-c26d-4e90-aa25-9268fba81d11")
    .AddEnvironmentVariables()
    .Build();
var connection = Environment.GetEnvironmentVariable("AUTH_TEST_MONGO")
    ?? configuration["MongoDbSettings:ConnectionString"];
if (string.IsNullOrWhiteSpace(connection))
    throw new Exception("Set AUTH_TEST_MONGO or MongoDbSettings:ConnectionString for auth integration checks.");

var mongoSettings = MongoClientSettings.FromConnectionString(connection);
mongoSettings.ServerSelectionTimeout = TimeSpan.FromSeconds(15);
var client = new MongoClient(mongoSettings);
var databaseName = "sgx_auth_" + MongoDB.Bson.ObjectId.GenerateNewId().ToString();
var database = client.GetDatabase(databaseName);
try
{
    var authService = new AuthService(database, configuration);
    await authService.EnsureIndexesAsync();
    var users = database.GetCollection<User>("Users");

    async Task Seed(User user, string password = "Password123!")
    {
        user.PasswordHash = BCrypt.Net.BCrypt.HashPassword(password);
        if (string.IsNullOrWhiteSpace(user.SecurityStamp))
            user.SecurityStamp = Guid.NewGuid().ToString("N");
        await users.InsertOneAsync(user);
    }

    await Seed(new User
    {
        NIC = "active-001",
        Name = "Active User",
        Email = "active@example.com",
        Role = "Backoffice",
        AccountStatus = AccountStatus.Active
    });
    await Seed(new User
    {
        NIC = "pending-001",
        Name = "Pending User",
        Email = "pending@example.com",
        Role = "Prosumer",
        AccountStatus = AccountStatus.Pending
    });
    await Seed(new User
    {
        NIC = "inactive-001",
        Name = "Inactive User",
        Email = "inactive@example.com",
        Role = "Prosumer",
        AccountStatus = AccountStatus.Inactive
    });

    var login = await authService.LoginUserAsync(new LoginUserDto
    {
        Email = "ACTIVE@example.com",
        Password = "Password123!"
    });
    Expect(login.Success && login.Data?.Role == "Backoffice", "active login succeeds");

    var wrongPassword = await authService.LoginUserAsync(new LoginUserDto
    {
        Email = "active@example.com",
        Password = "WrongPassword!"
    });
    Expect(!wrongPassword.Success, "wrong password is rejected");

    var pendingLogin = await authService.LoginUserAsync(new LoginUserDto
    {
        Email = "pending@example.com",
        Password = "Password123!"
    });
    Expect(!pendingLogin.Success && pendingLogin.ErrorMessage!.Contains("pending", StringComparison.OrdinalIgnoreCase), "pending account login is rejected");

    var inactiveLogin = await authService.LoginUserAsync(new LoginUserDto
    {
        Email = "inactive@example.com",
        Password = "Password123!"
    });
    Expect(!inactiveLogin.Success && inactiveLogin.ErrorMessage!.Contains("deactivated", StringComparison.OrdinalIgnoreCase), "inactive account login is rejected");

    Expect(await authService.RegisterUserAsync(new RegisterUserDto
    {
        NIC = "pending-001",
        Name = "Duplicate NIC",
        Email = "new@example.com",
        Password = "Password123!"
    }) == null, "duplicate NIC is rejected");
    Expect(await authService.RegisterUserAsync(new RegisterUserDto
    {
        NIC = "new-001",
        Name = "Duplicate Email",
        Email = "active@example.com",
        Password = "Password123!"
    }) == null, "duplicate email is rejected");

    var activated = await authService.UpdateAccountStatusAsync("pending-001", AccountStatus.Active, "active-001");
    Expect(activated.User?.AccountStatus == "Active", "pending account activates");
    var reactivated = await authService.UpdateAccountStatusAsync("inactive-001", AccountStatus.Active, "active-001");
    Expect(reactivated.User?.AccountStatus == "Active", "inactive account reactivates");

    var updated = await authService.UpdateProfileAsync("pending-001", "Updated User", "updated@example.com");
    Expect(updated?.Name == "Updated User" && updated.Email == "updated@example.com", "profile update persists");
    Expect(await authService.UpdateProfileAsync("pending-001", "Updated User", "active@example.com") == null, "profile duplicate email is rejected");

    Console.WriteLine("Passed Member 1 authentication and account lifecycle integration checks.");
}
finally
{
    await client.DropDatabaseAsync(databaseName);
    Console.WriteLine("Removed disposable auth test database.");
}
