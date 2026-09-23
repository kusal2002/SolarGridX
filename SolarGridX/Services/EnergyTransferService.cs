using MongoDB.Driver;
using SolarGridX.Models;

namespace SolarGridX.Services
{
    public class EnergyTransferService
    {
        private readonly IMongoCollection<EnergyTransfer> _transfers;

        public EnergyTransferService(IMongoDatabase database)
        {
            _transfers = database.GetCollection<EnergyTransfer>(
                "EnergyTransfers"
            );
        }

        //Get All Energy Transfers
        public async Task<List<EnergyTransfer>> GetAllAsync()
        {
            return await _transfers
                .Find(_ => true)
                .ToListAsync();
        }

        //Get A Energy Transfer By Id
        public async Task<EnergyTransfer?> GetByIdAsync(string id)
        {
            return await _transfers
                .Find(t => t.id == id)
                .FirstOrDefaultAsync();
        }

        //Create A New Energy Transfer
        public async Task CreateAsync(EnergyTransfer transfer)
        {
            await _transfers.InsertOneAsync(transfer);
        }
    }
}
