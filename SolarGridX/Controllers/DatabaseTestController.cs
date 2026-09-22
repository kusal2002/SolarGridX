using Microsoft.AspNetCore.Mvc;
using MongoDB.Bson;
using MongoDB.Driver;

namespace SolarGridX.Controllers
{
    [ApiController]
    [Route("/api/database-test")]

    public class DatabaseTestController : Controller
    {
        private readonly IMongoDatabase _database;

        public DatabaseTestController(IMongoDatabase database)
        {
            _database = database;
        }

        [HttpGet]
        public async Task<IActionResult> TestConnection()
        {
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
    }
}
