// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: MongoDbSettings.cs
// Description: Configuration model for MongoDB connection string and database name.
// ============================================================================

namespace SolarGridX.Settings
{
    public class MongoDbSettings
    {
        public string ConnectionString { get; set; } = string.Empty;

        public string DatabaseName { get; set; } = string.Empty;
    }
}
