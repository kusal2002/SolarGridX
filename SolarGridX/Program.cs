// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: Program.cs
// Description: Main entry point, dependency injection container setup, JWT configuration, and database initialization.
// ============================================================================

using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.Extensions.Options;
using MongoDB.Driver;
using Microsoft.IdentityModel.Tokens;
using System.Security.Claims;
using SolarGridX.Services;
using SolarGridX.Settings;

var builder = WebApplication.CreateBuilder(args);

// Add services to the container.

builder.Services.Configure<MongoDbSettings>(
    builder.Configuration.GetSection("MongoDbSettings")
);

// Register application domain services
builder.Services.AddScoped<EnergyTransferService>();
builder.Services.AddDataProtection().SetApplicationName("SolarGridX");
builder.Services.AddScoped<ReservationQrService>();
builder.Services.AddScoped<AuthService>();

// Stations service registration
builder.Services.AddScoped<StationService>();
builder.Services.AddScoped<StationAccessService>();
// Slot service registration
builder.Services.AddScoped<SlotService>();
// Reservation service registration (Member 3)
builder.Services.AddScoped<ReservationService>();

// Configure JWT Authentication
var jwtSettings = builder.Configuration.GetSection("Jwt");
var jwtKey = jwtSettings["Key"]
    ?? Environment.GetEnvironmentVariable("Jwt__Key")
    ?? Environment.GetEnvironmentVariable("Jwt__Key", EnvironmentVariableTarget.Machine)
    ?? Environment.GetEnvironmentVariable("Jwt__Key", EnvironmentVariableTarget.User)
    ?? throw new InvalidOperationException("JWT key is not configured.");
var jwtKeyBytes = System.Text.Encoding.UTF8.GetBytes(jwtKey);

if (jwtKeyBytes.Length < 32)
{
    throw new InvalidOperationException("Jwt:Key must be at least 32 bytes for HS256.");
}

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        // Set up JWT token validation parameters
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuerSigningKey = true,
            IssuerSigningKey = new SymmetricSecurityKey(jwtKeyBytes),
            ValidateIssuer = true,
            ValidIssuer = jwtSettings["Issuer"],
            ValidateAudience = true,
            ValidAudience = jwtSettings["Audience"],
            ValidateLifetime = true,
            ClockSkew = TimeSpan.Zero
        };

        options.Events = new JwtBearerEvents
        {
            OnTokenValidated = async context =>
            {
                // Verify security stamp on each request to invalidate revoked tokens
                var nic = context.Principal?.FindFirstValue(ClaimTypes.NameIdentifier);
                var securityStamp = context.Principal?.FindFirstValue("security_stamp");

                if (string.IsNullOrWhiteSpace(nic) || string.IsNullOrWhiteSpace(securityStamp))
                {
                    context.Fail("The token does not contain a valid user identity.");
                    return;
                }

                var authService = context.HttpContext.RequestServices.GetRequiredService<AuthService>();
                if (!await authService.IsTokenActiveAsync(nic, securityStamp,
                    context.Principal?.FindFirstValue(ClaimTypes.Role)))
                {
                    context.Fail("The account is inactive or the token has been revoked.");
                }
            }
        };
    });


// Register MongoDB client singleton
builder.Services.AddSingleton<IMongoClient>(sp =>
{
    // Retrieve connection string from configuration or environment variables
    var settings = sp.GetRequiredService<IOptions<MongoDbSettings>>().Value;
    var conn = settings.ConnectionString;
    if (string.IsNullOrWhiteSpace(conn))
    {
        conn = Environment.GetEnvironmentVariable("MongoDbSettings__ConnectionString")
            ?? Environment.GetEnvironmentVariable("MongoDbSettings__ConnectionString", EnvironmentVariableTarget.Machine)
            ?? Environment.GetEnvironmentVariable("MongoDbSettings__ConnectionString", EnvironmentVariableTarget.User)
            ?? "";
    }
    return new MongoClient(conn);
});

// Register MongoDB database singleton
builder.Services.AddSingleton<IMongoDatabase>(sp =>
{
    // Connect to the configured MongoDB database
    var settings = sp.GetRequiredService<IOptions<MongoDbSettings>>().Value;
    var client = sp.GetRequiredService<IMongoClient>();
    return client.GetDatabase(settings.DatabaseName);
});

builder.Services.AddControllers();
// Require authentication by default; only registration and login opt out.
builder.Services.AddAuthorization(options =>
{
    // Fallback policy requires all endpoints to be authenticated unless AllowAnonymous is specified
    options.FallbackPolicy = new AuthorizationPolicyBuilder()
        .RequireAuthenticatedUser().Build();
});
// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();

// Configure CORS for local web client
builder.Services.AddCors(options =>
{
    options.AddPolicy("ClientPolicy", policy =>
    {
        policy.WithOrigins("http://localhost:5173", "http://127.0.0.1:5173")
              .AllowAnyHeader()
              .AllowAnyMethod();
    });
});

var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

// if (!app.Environment.IsDevelopment())
// {
//     app.UseHttpsRedirection();
// }
app.UseCors("ClientPolicy");

app.UseAuthentication();
app.UseAuthorization();

app.MapControllers();

// Initialize database indexes and bootstrap default backoffice admin
using (var scope = app.Services.CreateScope())
{
    var configuration = scope.ServiceProvider.GetRequiredService<IConfiguration>();
    var authService = scope.ServiceProvider.GetRequiredService<AuthService>();
    await authService.EnsureIndexesAsync();
    await scope.ServiceProvider.GetRequiredService<EnergyTransferService>().EnsureIndexesAsync();
    await authService.EnsureBootstrapBackofficeAsync(
        configuration["BootstrapAdmin:NIC"],
        configuration["BootstrapAdmin:Name"],
        configuration["BootstrapAdmin:Email"],
        configuration["BootstrapAdmin:Password"]);
}

app.Run();
