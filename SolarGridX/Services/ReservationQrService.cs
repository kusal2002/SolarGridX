using System.Security.Cryptography;
using System.Text.Json;
using Microsoft.AspNetCore.DataProtection;

namespace SolarGridX.Services;

// Separate purpose keeps QR tokens independent of login tokens.
public class ReservationQrService(IDataProtectionProvider provider)
{
    private readonly IDataProtector _protector = provider.CreateProtector("SolarGridX.ReservationQR.v1");
    public record Ticket(string ReservationId, long Version, DateTime ExpiresAt);

    public object Issue(Models.EnergyReservation reservation)
    {
        var ticket = new Ticket(reservation.Id, reservation.UpdatedAt.Ticks, DateTime.UtcNow.AddMinutes(15));
        return new { payload = _protector.Protect(JsonSerializer.Serialize(ticket)), ticket.ExpiresAt };
    }

    public Ticket Read(string payload)
    {
        try
        {
            var ticket = JsonSerializer.Deserialize<Ticket>(_protector.Unprotect(payload));
            if (ticket == null || ticket.ExpiresAt <= DateTime.UtcNow)
                throw new TransferException(400, "QR expired. Ask the prosumer to refresh it.");
            return ticket;
        }
        catch (Exception ex) when (ex is CryptographicException or JsonException or ArgumentException)
        {
            throw new TransferException(400, "Invalid QR code.");
        }
    }
}
