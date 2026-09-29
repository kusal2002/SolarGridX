using System.ComponentModel.DataAnnotations;
using Microsoft.Extensions.Configuration;
using MongoDB.Bson;
using MongoDB.Driver;
using SolarGridX.DTOs;
using SolarGridX.DTOs.Reservations;
using SolarGridX.Models;
using SolarGridX.Services;

var checks = 0;
void Check(bool ok, string description) { if (!ok) throw new Exception("FAILED: " + description); checks++; }
async Task Reject(Func<Task> call, int status)
{
    try { await call(); throw new Exception("Expected rejection " + status); }
    catch (TransferException e) { Check(e.StatusCode == status, e.Message); }
}
async Task RejectReservation(Func<Task> call)
{
    try { await call(); throw new Exception("Expected reservation guard"); }
    catch (InvalidOperationException) { checks++; }
}
bool Valid(object value) => Validator.TryValidateObject(value, new ValidationContext(value), [], true);
Check(!Valid(new CreateEnergyTransferRequest()), "empty create");
Check(!Valid(new CreateEnergyTransferRequest { ReservationId = "RES001", SellerId = "s", BuyerId = "b", ExpectedEnergyKWh = 5 }), "invalid ObjectId");
Check(!Valid(new TransferEnergyRequest()), "missing meter value");
Check(!Valid(new TransferEnergyRequest { TransferredEnergyKWh = -1 }), "negative meter value");
Check(!Valid(new EndEnergyTransferRequest { Reason = "   " }), "blank reason");
Check(!Valid(new EnergyTransferQuery { Page = 0, PageSize = 101 }), "bounded pagination");
foreach (var state in new[] { "Pending", "InProgress", "Completed", "Cancelled", "Failed" })
foreach (var action in new[] { "start", "progress", "complete", "cancel", "fail" })
{
    var transfer = new EnergyTransfer { Status = state, ExpectedEnergyKWh = 5 };
    var allowed = (state == "Pending" && action is "start" or "cancel") ||
        (state == "InProgress" && action is "progress" or "complete" or "fail");
    try { EnergyTransferRules.Apply(transfer, action, 5, "test", "operator", DateTime.UtcNow); Check(allowed, state + action); }
    catch (TransferException ex) { Check(!allowed && ex.StatusCode == 409, state + action); }
}
var meter = new EnergyTransfer { Status = "InProgress", ExpectedEnergyKWh = 5, TransferredEnergyKWh = 2 };
foreach (var value in new decimal?[] { null, -1, 1, 6 })
    await Reject(() => { EnergyTransferRules.Apply(meter, "progress", value, null, "op", DateTime.UtcNow); return Task.CompletedTask; }, 400);
EnergyTransferRules.Apply(meter, "progress", 2, null, "op", DateTime.UtcNow);
Check(meter.History.Count == 0, "repeated meter reading is idempotent");
await Reject(() => { EnergyTransferRules.Apply(meter, "complete", 4, null, "op", DateTime.UtcNow); return Task.CompletedTask; }, 400);
Console.WriteLine($"Passed {checks} validation/lifecycle checks.");
if (!args.Contains("--integration")) return;

// Use application credentials but NEVER its database: all fixtures live in a unique disposable database.
var configuration = new ConfigurationBuilder().SetBasePath(Path.GetFullPath("SolarGridX"))
    .AddJsonFile("appsettings.json").AddJsonFile("appsettings.Development.json", true)
    .AddUserSecrets("8516e0c0-c26d-4e90-aa25-9268fba81d11").AddEnvironmentVariables().Build();
var connection = Environment.GetEnvironmentVariable("TRANSFER_TEST_MONGO") ?? configuration["MongoDbSettings:ConnectionString"];
if (string.IsNullOrWhiteSpace(connection)) throw new Exception("Set TRANSFER_TEST_MONGO to a replica-set MongoDB connection.");
var settings = MongoClientSettings.FromConnectionString(connection);
settings.ServerSelectionTimeout = TimeSpan.FromSeconds(15);
var client = new MongoClient(settings);
var dbName = "sgx_transfer_" + ObjectId.GenerateNewId().ToString();
var db = client.GetDatabase(dbName);
try
{
    var service = new EnergyTransferService(db);
    var reservations = new ReservationService(db);
    await service.EnsureIndexesAsync();
    await db.GetCollection<User>("Users").InsertManyAsync(new[] {
        new User { NIC = "seller" }, new User { NIC = "buyer" },
        new User { NIC = "inactive", AccountStatus = "Inactive" } });
    var station = new SolarStation { Id = ObjectId.GenerateNewId().ToString() };
    await db.GetCollection<SolarStation>("SolarStationInfo").InsertOneAsync(station);
    var slot = new EnergyBookingSlot { Id = ObjectId.GenerateNewId().ToString(), StationId = station.Id,
        SlotDate = DateTime.UtcNow.Date.AddDays(2), EnergyCapacityKwh = 100, AvailableEnergyKwh = 100 };
    var slots = db.GetCollection<EnergyBookingSlot>("EnergyBookingSlots");
    await slots.InsertOneAsync(slot);
    async Task<EnergyReservation> Booking(bool approve = true)
    {
        // Unique slots avoid the application's one active booking per prosumer/slot constraint.
        var fixtureSlot = new EnergyBookingSlot { Id = ObjectId.GenerateNewId().ToString(), StationId = station.Id,
            SlotDate = slot.SlotDate, EnergyCapacityKwh = 100, AvailableEnergyKwh = 100 };
        await slots.InsertOneAsync(fixtureSlot);
        var r = await reservations.CreateAsync(new CreateReservationRequest { ProsumerNIC = "buyer", SlotId = fixtureSlot.Id, RequestedEnergyKwh = 5 });
        if (approve) r = await reservations.UpdateStatusAsync(r.Id, "Approved");
        return r;
    }
    CreateEnergyTransferRequest Request(EnergyReservation r) => new() { ReservationId = r.Id, SellerId = "seller", BuyerId = "buyer", ExpectedEnergyKWh = 5 };
    Task<EnergyTransfer> Change(EnergyTransfer t, string action, decimal? value = null) => service.ChangeAsync(t.id!, action, value, "test reason", "operator");
    var pending = await Booking(false);
    await Reject(() => service.CreateAsync(Request(pending), "operator"), 409);
    var booking = await Booking();
    var wrong = Request(booking); wrong.BuyerId = "seller"; wrong.SellerId = "buyer";
    await Reject(() => service.CreateAsync(wrong, "operator"), 400);
    wrong = Request(booking); wrong.ExpectedEnergyKWh = 4;
    await Reject(() => service.CreateAsync(wrong, "operator"), 400);
    wrong = Request(booking); wrong.SellerId = "missing";
    await Reject(() => service.CreateAsync(wrong, "operator"), 404);
    wrong.SellerId = "inactive";
    await Reject(() => service.CreateAsync(wrong, "operator"), 409);
    await Reject(() => service.GetByIdAsync("bad-id"), 400);
    Check(await service.GetByIdAsync(ObjectId.GenerateNewId().ToString()) == null, "unknown ID");

    var attempts = await Task.WhenAll(Enumerable.Range(0, 6).Select(async _ =>
    {
        try { return await service.CreateAsync(Request(booking), "operator"); }
        catch (TransferException e) when (e.StatusCode == 409) { return null; }
    }));
    Check(attempts.Count(t => t != null) == 1, "concurrent creation yields one transfer");
    var transfer = attempts.Single(t => t != null)!;
    await RejectReservation(() => reservations.CancelAsync(booking.Id, "blocked"));
    await RejectReservation(() => reservations.UpdateAsync(booking.Id, new UpdateReservationRequest { RequestedEnergyKwh = 4 }));
    await RejectReservation(() => reservations.UpdateStatusAsync(booking.Id, "Completed"));
    await Reject(() => Change(transfer, "complete", 5), 409);
    transfer = await Change(transfer, "start");
    Check(transfer.Status == "InProgress" && transfer.StartedAt != null, "start");
    await Change(transfer, "progress", 2);
    await Reject(() => Change(transfer, "progress", 1), 400);
    await Reject(() => Change(transfer, "progress", 6), 400);
    await Reject(() => Change(transfer, "complete", 4), 400);
    transfer = await Change(transfer, "complete", 5);
    Check(transfer.Status == "Completed" && transfer.CompletedAt != null && transfer.History.Count == 4, "completion/history");
    Check((await reservations.GetByIdAsync(booking.Id))!.Status == "Completed", "reservation completed atomically");
    await Reject(() => Change(transfer, "complete", 5), 409);
    Check((await slots.Find(s => s.Id == booking.SlotId).FirstAsync()).AvailableEnergyKwh == 95, "completed energy not released");

    var cancelBooking = await Booking();
    var cancelTransfer = await service.CreateAsync(Request(cancelBooking), "operator");
    var endings = await Task.WhenAll(Enumerable.Range(0, 4).Select(async _ =>
    {
        try { await Change(cancelTransfer, "cancel"); return true; }
        catch (TransferException e) when (e.StatusCode == 409) { return false; }
    }));
    Check(endings.Count(x => x) == 1, "concurrent cancellation succeeds once");
    Check((await slots.Find(s => s.Id == cancelBooking.SlotId).FirstAsync()).AvailableEnergyKwh == 100, "capacity restored once");
    await Reject(() => service.CreateAsync(Request(cancelBooking), "operator"), 409);
    var failBooking = await Booking();
    var failed = await service.CreateAsync(Request(failBooking), "operator");
    await Change(failed, "start");
    await Change(failed, "progress", 2);
    failed = await Change(failed, "fail");
    Check(failed.TransferredEnergyKWh == 2 && failed.Status == "Failed", "partial failure");
    Check((await slots.Find(s => s.Id == failBooking.SlotId).FirstAsync()).AvailableEnergyKwh == 98, "only unused energy released");
    Check((await reservations.GetByIdAsync(failBooking.Id))!.Status == "Cancelled", "failed reservation closed");
    Check((await service.GetAllAsync(new EnergyTransferQuery { Status = "Completed", PageSize = 1 })).Count == 1, "filtered paginated list");
    // Force a missing dependency after creation: transaction must leave transfer and reservation untouched.
    var rollbackBooking = await Booking();
    var rollback = await service.CreateAsync(Request(rollbackBooking), "operator");
    await slots.DeleteOneAsync(s => s.Id == rollbackBooking.SlotId);
    await Reject(() => Change(rollback, "cancel"), 409);
    Check((await service.GetByIdAsync(rollback.id!))!.Status == "Pending", "failed cancellation rolls back transfer");
    Check((await reservations.GetByIdAsync(rollbackBooking.Id))!.Status == "Approved", "failed cancellation rolls back reservation");
    // A reservation cancellation racing a transfer claim must have exactly one winner.
    var raceBooking = await Booking();
    var createRace = Task.Run(async () =>
    {
        try { await service.CreateAsync(Request(raceBooking), "operator"); return true; }
        catch (TransferException e) when (e.StatusCode == 409) { return false; }
    });
    var cancelRace = Task.Run(async () =>
    {
        try { await reservations.CancelAsync(raceBooking.Id, "race"); return true; }
        catch (InvalidOperationException) { return false; }
    });
    await Task.WhenAll(createRace, cancelRace);
    Check(createRace.Result != cancelRace.Result, "transfer creation versus reservation cancellation has one winner");
    var racedReservation = (await reservations.GetByIdAsync(raceBooking.Id))!;
    Check(createRace.Result ? racedReservation.TransferId != null && racedReservation.Status == "Approved"
        : racedReservation.TransferId == null && racedReservation.Status == "Cancelled", "race preserves reservation state");
    Check((await slots.Find(s => s.Id == raceBooking.SlotId).FirstAsync()).AvailableEnergyKwh == (createRace.Result ? 95 : 100),
        "race preserves capacity");
    Console.WriteLine($"Passed {checks} total checks, including MongoDB transactions, races, rollback, and capacity accounting.");
}
finally
{
    if (!dbName.StartsWith("sgx_transfer_", StringComparison.Ordinal)) throw new Exception("Invalid cleanup database");
    await client.DropDatabaseAsync(dbName);
    Console.WriteLine("Removed disposable transfer test database.");
}
