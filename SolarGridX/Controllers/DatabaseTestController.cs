// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: DatabaseTestController.cs
// Description: Controller for testing database connectivity and retrieving raw debug collection data.
// ============================================================================

using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using MongoDB.Bson;
using MongoDB.Driver;

namespace SolarGridX.Controllers
{
    [Authorize(Roles = "Backoffice")]
    [ApiController]
    [Route("/api/database-test")]

    public class DatabaseTestController : Controller
    {
        private readonly IMongoDatabase _database;

        public DatabaseTestController(IMongoDatabase database)
        {
            // Inject MongoDB database instance
            _database = database;
        }

        [HttpGet]
        public async Task<IActionResult> TestConnection()
        {
            // Ping the MongoDB database to verify successful connection
            try
            {
                await _database.RunCommandAsync<BsonDocument>(
                    new BsonDocument("ping", 1)
                );


                return Ok(new
                {
                    message = "MongoDB Connected Successfully!",
                    DatabaseNamespace = _database.DatabaseNamespace.DatabaseName
                });
            }
            catch (Exception ex)
            {
                return StatusCode(503, new
                {
                    message = "Database connection failed.",
                    error = ex.Message
                });
            }
        }
        [HttpGet("stations-debug")]
        public async Task<IActionResult> GetStationsDebug()
        {
            // Fetch raw station documents directly for testing and diagnostics
            try
            {
                var collection = _database.GetCollection<BsonDocument>("SolarStationInfo");
                var stations = await collection.Find(new BsonDocument()).ToListAsync();
                return Ok(new
                {
                    count = stations.Count,
                    stations = stations.Select(s => BsonTypeMapper.MapToDotNetValue(s))
                });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = ex.Message });
            }
        }

        [HttpGet("slots-debug")]
        public async Task<IActionResult> GetSlotsDebug()
        {
            // Fetch raw booking slot documents directly for testing and diagnostics
            try
            {
                var collection = _database.GetCollection<BsonDocument>("EnergyBookingSlots");
                var slots = await collection.Find(new BsonDocument()).ToListAsync();
                return Ok(new
                {
                    count = slots.Count,
                    slots = slots.Select(s => BsonTypeMapper.MapToDotNetValue(s))
                });
            }
            catch (Exception ex)
            {
                return StatusCode(500, new { error = ex.Message });
            }
        }
    }
}
