using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using SolarGridX.Services;
using SolarGridX.DTOs;
using SolarGridX.Models;

namespace SolarGridX.Controllers
{
    [Route("api/energy-transfers")]
    [ApiController]
    public class EnergyTransfersController : ControllerBase
    {
        private readonly EnergyTransferService _transferservice;

        public EnergyTransfersController(
            EnergyTransferService transferservice)
        {
            _transferservice = transferservice;
        }

        //GET: api/energy-transfers
        [HttpGet]
        public async Task<IActionResult> GetAll()
        {
            var transfers = await _transferservice.GetAllAsync();
            return Ok(transfers);
        }

        // POST: api/energy-transfers
        [HttpPost]
        public async Task<IActionResult> Create(
            [FromBody] CreateEnergyTransferRequest request)
        {
            if (string.IsNullOrWhiteSpace(request.ReservationId) ||
                string.IsNullOrWhiteSpace(request.SellerId) ||
                string.IsNullOrWhiteSpace(request.BuyerId))
            {
                return BadRequest(new
                {
                    message = "Reservation, seller and buyer IDs are required."
                });
            }

            if (request.SellerId == request.BuyerId)
            {
                return BadRequest(new
                {
                    message = "Seller and buyer cannot be the same user."
                });
            }

            var transfer = new EnergyTransfer
            {
                ReservationId = request.ReservationId,
                SellerId = request.SellerId,
                BuyerId = request.BuyerId,
                ExpectedEnergyKWh = request.ExpectedEnergyKWh,
                TransferredEnergyKWh = 0,
                Status = "Pending",
                CreatedAt = DateTime.UtcNow
            };

            await _transferservice.CreateAsync(transfer);

            return StatusCode(201, transfer);
        }
    }
}
