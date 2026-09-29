// Authorization regression checks; uses ASP.NET's policy evaluator without a database.
using System.Reflection;
using System.Security.Claims;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.Mvc.Routing;
using Microsoft.Extensions.DependencyInjection;
using SolarGridX.Controllers;
using SolarGridX.DTOs;
using SolarGridX.DTOs.Reservations;

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
foreach (var action in new[] { "GetAll", "Create" })
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
