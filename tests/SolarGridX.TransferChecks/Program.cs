using System.ComponentModel.DataAnnotations;
using System.Text.Json;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using System.Security.Claims;
using SolarGridX.Controllers;
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
Check(!Valid(new CreateEnergyTransferRequest { ReservationId = "RES001" }), "invalid ObjectId");
Check(Valid(new CreateEnergyTransferRequest { ReservationId = ObjectId.GenerateNewId().ToString() }), "reservation alone creates transfer request");
Check(!Valid(new TransferEnergyRequest()), "missing meter value");
Check(!Valid(new TransferEnergyRequest { TransferredEnergyKWh = -1 }), "negative meter value");
Check(!Valid(new EndEnergyTransferRequest { Reason = "   " }), "blank reason");
Check(!Valid(new EnergyTransferQuery { Page = 0, PageSize = 101 }), "bounded pagination");
Check(!Valid(new VerifyTransferRequest()), "QR payload required");
Check(Valid(new VerifyTransferRequest { Payload = "protected-qr" }), "verification requires no second account");
var protection = new EphemeralDataProtectionProvider();
var qr = new ReservationQrService(protection);
string Payload(EnergyReservation r) => JsonSerializer.SerializeToElement(qr.Issue(r)).GetProperty("payload").GetString()!;
var sample = new EnergyReservation { Id = ObjectId.GenerateNewId().ToString(), UpdatedAt = DateTime.UtcNow };
var payload = Payload(sample);
Check(qr.Read(payload).ReservationId == sample.Id, "QR round trip");
Check(!payload.Contains(sample.Id), "QR does not expose reservation ID");
await Reject(() => { qr.Read(payload[..^5] + "xxxxx"); return Task.CompletedTask; }, 400);
await Reject(() => { qr.Read("not-a-qr"); return Task.CompletedTask; }, 400);
var expired = protection.CreateProtector("SolarGridX.ReservationQR.v1").Protect(JsonSerializer.Serialize(
    new ReservationQrService.Ticket(sample.Id, sample.UpdatedAt.Ticks, DateTime.UtcNow.AddMinutes(-1))));
await Reject(() => { qr.Read(expired); return Task.CompletedTask; }, 400);
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
var noOperatorIdentity = new ClaimsPrincipal(new ClaimsIdentity([new Claim(ClaimTypes.Role, "Grid Operator")], "test"));
Check((await new StationAccessService(null!).StationIdsAsync(noOperatorIdentity))!.Count == 0, "missing operator identity cannot claim unassigned stations");
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
        new User { NIC = "prosumer", Name = "Test Prosumer" },
        new User { NIC = "operator", Role = "Grid Operator" },
        new User { NIC = "operator-two", Role = "Grid Operator" },
        new User { NIC = "inactive", AccountStatus = AccountStatus.Inactive } });
    var station = new SolarStation { Id = ObjectId.GenerateNewId().ToString(), StationName = "Test Microgrid Station", OperatorNIC = "operator" };
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
        var r = await reservations.CreateAsync(new CreateReservationRequest { ProsumerNIC = "prosumer", SlotId = fixtureSlot.Id, RequestedEnergyKwh = 5 });
        if (approve) r = await reservations.UpdateStatusAsync(r.Id, "Approved");
        return r;
    }
    CreateEnergyTransferRequest Request(EnergyReservation r) => new() { ReservationId = r.Id };
    Task<EnergyTransfer> Change(EnergyTransfer t, string action, decimal? value = null) => service.ChangeAsync(t.id!, action, value, "test reason", "operator");
    var pending = await Booking(false);
    await Reject(() => service.CreateAsync(Request(pending), "operator"), 409);
    var booking = await Booking();
    ControllerContext Context(string nic, string role = "Prosumer") => new() { HttpContext = new DefaultHttpContext { User = new ClaimsPrincipal(
        new ClaimsIdentity(new[] { new Claim(ClaimTypes.NameIdentifier, nic), new Claim(ClaimTypes.Role, role) }, "test")) } };
    var stationAccess = new StationAccessService(db);
    var stationService = new StationService(db);
    var stationApi = new StationController(stationService, stationAccess) { ControllerContext = Context("operator", "Grid Operator") };
    Check(((OkObjectResult)await stationApi.GetAll()).Value is List<SolarStation> assigned && assigned.Count == 1, "operator station list scoped to assignment");
    var foreignStation = new SolarStation { Id = ObjectId.GenerateNewId().ToString(), StationName = "Other station", OperatorNIC = "different-operator" };
    await db.GetCollection<SolarStation>("SolarStationInfo").InsertOneAsync(foreignStation);
    Check(await stationApi.GetById(foreignStation.Id) is ForbidResult, "foreign station detail blocked");
    var slotApi = new SlotController(new SlotService(db), stationAccess) { ControllerContext = Context("operator", "Grid Operator") };
    Check(await slotApi.GetByStationIdIncludingInactive(foreignStation.Id) is ForbidResult, "foreign station slot list blocked");
    try { await stationService.AssignOperatorAsync(station.Id, "prosumer"); throw new Exception("Expected invalid assignment"); }
    catch (ArgumentException) { checks++; }
    Check((await stationService.AssignOperatorAsync(station.Id, "operator"))!.OperatorNIC == "operator", "active operator can be assigned");
    var qrController = new ReservationController(reservations, qr, stationAccess) { ControllerContext = Context("other-buyer") };
    Check(await qrController.GetQr(booking.Id) is ForbidResult, "another prosumer cannot get QR");
    qrController.ControllerContext = Context("prosumer");
    Check(await qrController.GetQr(pending.Id) is ConflictObjectResult, "pending booking cannot issue QR");
    Check(await qrController.GetQr(booking.Id) is OkObjectResult, "approved owner can get QR");
    var dashboard = new BookingsController(reservations, new StationService(db), stationAccess, db) { ControllerContext = Context("prosumer") };
    var counts = JsonSerializer.SerializeToElement(((OkObjectResult)await dashboard.Summary()).Value);
    Check(counts.GetProperty("pending").GetInt32() == 1 && counts.GetProperty("approvedFuture").GetInt32() == 1, "live future/pending counts");
    var namedBookings = JsonSerializer.SerializeToElement(((OkObjectResult)await dashboard.List("current")).Value);
    Check(namedBookings.GetProperty("items")[0].GetProperty("StationName").GetString() == station.StationName, "booking includes station name");
    dashboard.ControllerContext = Context("other-buyer");
    var ownList = JsonSerializer.SerializeToElement(((OkObjectResult)await dashboard.List("search")).Value);
    Check(ownList.GetProperty("total").GetInt32() == 0, "booking list scoped to owner");
    Check(await dashboard.List("current", page: 0) is BadRequestObjectResult, "bad booking pagination rejected");
    var reservationCollection = db.GetCollection<EnergyReservation>("EnergyReservations");
    await reservationCollection.UpdateOneAsync(r => r.Id == booking.Id, Builders<EnergyReservation>.Update.Set(r => r.ProsumerNIC, "missing"));
    await Reject(() => service.CreateAsync(Request(booking), "operator"), 404);
    await reservationCollection.UpdateOneAsync(r => r.Id == booking.Id, Builders<EnergyReservation>.Update.Set(r => r.ProsumerNIC, "inactive"));
    await Reject(() => service.CreateAsync(Request(booking), "operator"), 409);
    await reservationCollection.UpdateOneAsync(r => r.Id == booking.Id, Builders<EnergyReservation>.Update.Set(r => r.ProsumerNIC, "prosumer"));
    await Reject(() => service.GetByIdAsync("bad-id"), 400);
    Check(await service.GetByIdAsync(ObjectId.GenerateNewId().ToString()) == null, "unknown ID");

    var attempts = await Task.WhenAll(Enumerable.Range(0, 6).Select(async _ =>
    {
        try { return await service.CreateAsync(Request(booking), "operator"); }
        catch (TransferException e) when (e.StatusCode == 409) { return null; }
    }));
    Check(attempts.Count(t => t != null) == 1, "concurrent creation yields one transfer");
    var transfer = attempts.Single(t => t != null)!;
    Check(transfer.ProsumerNIC == "prosumer" && transfer.StationId == booking.StationId && transfer.SlotId == booking.SlotId && transfer.ExpectedEnergyKWh == 5,
        "transfer identity and energy come from reservation with only one active prosumer");
    await RejectReservation(() => reservations.CancelAsync(booking.Id, "blocked"));
    await RejectReservation(() => reservations.UpdateAsync(booking.Id, new UpdateReservationRequest { RequestedEnergyKwh = 4 }));
    await RejectReservation(() => reservations.UpdateStatusAsync(booking.Id, "Completed"));
    await Reject(() => Change(transfer, "complete", 5), 409);
    await Reject(() => Change(transfer, "start"), 409);
    booking = (await reservations.GetByIdAsync(booking.Id))!;
    await Reject(() => service.CreateAsync(Request(booking), "operator", ticket:
        new ReservationQrService.Ticket(booking.Id, booking.UpdatedAt.Ticks - 1, DateTime.UtcNow.AddMinutes(15))), 409);
    transfer = await service.CreateAsync(Request(booking), "operator", ticket: qr.Read(Payload(booking)));
    Check(transfer.VerifiedBy == "operator" && transfer.VerifiedAt != null, "QR verification audited");
    transfer = await Change(transfer, "start");
    Check(transfer.Status == "InProgress" && transfer.StartedAt != null, "start");
    await Change(transfer, "progress", 2);
    await Reject(() => Change(transfer, "progress", 1), 400);
    await Reject(() => Change(transfer, "progress", 6), 400);
    await Reject(() => Change(transfer, "complete", 4), 400);
    transfer = await Change(transfer, "complete", 5);
    Check(transfer.Status == "Completed" && transfer.CompletedAt != null && transfer.History.Count == 5, "completion/history");
    Check((await reservations.GetByIdAsync(booking.Id))!.Status == "Completed", "reservation completed atomically");
    var historyApi = new EnergyTransfersController(service, qr, reservations, stationAccess) { ControllerContext = Context("prosumer") };
    Check(await historyApi.GetById(transfer.id!) is OkObjectResult, "prosumer can read own completed transfer");
    historyApi.ControllerContext = Context("other-prosumer");
    Check(await historyApi.GetById(transfer.id!) is ForbidResult, "prosumer cannot read another owner's transfer");
    historyApi.ControllerContext = Context("unassigned-operator", "Grid Operator");
    Check(await historyApi.GetById(transfer.id!) is ForbidResult, "unassigned operator cannot read transfer");
    Check(await historyApi.Start(transfer.id!) is ForbidResult, "unassigned operator cannot mutate transfer");
    historyApi.ControllerContext = Context("operator", "Grid Operator");
    Check(await historyApi.GetById(transfer.id!) is OkObjectResult, "assigned operator reads completed transfer");
    dashboard.ControllerContext = Context("operator", "Grid Operator");
    var completedList = JsonSerializer.SerializeToElement(((OkObjectResult)await dashboard.List("completed")).Value);
    Check(completedList.GetProperty("items")[0].GetProperty("ProsumerName").GetString() == "Test Prosumer", "booking includes scoped prosumer display name");
    Check(completedList.GetProperty("total").GetInt32() == 1, "completed view includes finished booking");
    var stats = JsonSerializer.SerializeToElement(((OkObjectResult)await dashboard.Summary()).Value);
    Check(stats.GetProperty("completedTransfers").GetInt32() == 1 && stats.GetProperty("deliveredEnergyKwh").GetDecimal() == 5, "dashboard totals completed delivery");
    dashboard.ControllerContext = Context("unassigned-operator", "Grid Operator");
    Check(await dashboard.Summary(station.Id) is ForbidResult, "foreign station filter rejected");
    stats = JsonSerializer.SerializeToElement(((OkObjectResult)await dashboard.Summary()).Value);
    Check(stats.GetProperty("completedTransfers").GetInt32() == 0 && stats.GetProperty("stationCount").GetInt32() == 0, "unassigned operator sees no system stats");
    var guardedReservation = new ReservationController(reservations, qr, stationAccess) { ControllerContext = Context("unassigned-operator", "Grid Operator") };
    Check(await guardedReservation.UpdateStatus(booking.Id, new UpdateReservationStatusRequest { Status = "Approved" }) is ForbidResult, "foreign booking status mutation blocked");
    historyApi.ControllerContext = Context("prosumer");
    var ownTransfers = JsonSerializer.SerializeToElement(((OkObjectResult)await historyApi.GetAll(new EnergyTransferQuery { Status = "Completed" })).Value);
    Check(ownTransfers.GetArrayLength() == 1, "prosumer completed transfer list is visible");
    historyApi.ControllerContext = Context("other-prosumer");
    var otherTransfers = JsonSerializer.SerializeToElement(((OkObjectResult)await historyApi.GetAll(new EnergyTransferQuery { Status = "Completed" })).Value);
    Check(otherTransfers.GetArrayLength() == 0, "prosumer list cannot leak another owner's transfers");
    await db.GetCollection<EnergyTransfer>("EnergyTransfers").UpdateOneAsync(t => t.id == transfer.id,
        Builders<EnergyTransfer>.Update.Unset(t => t.ProsumerNIC).Unset(t => t.StationId).Unset(t => t.SlotId));
    var legacy = await service.GetByIdAsync(transfer.id!);
    Check(legacy!.ProsumerNIC == "prosumer" && legacy.StationId == booking.StationId && legacy.SlotId == booking.SlotId, "older completed records recover identity from reservation");
    historyApi.ControllerContext = Context("operator", "Grid Operator");
    var stationHistory = JsonSerializer.SerializeToElement(((OkObjectResult)await historyApi.GetAll(new EnergyTransferQuery { Status = "Completed", StationId = station.Id })).Value);
    Check(stationHistory.GetArrayLength() == 1, "station history retains legacy completed records");
    await Reject(() => Change(transfer, "complete", 5), 409);
    await Reject(() => service.CreateAsync(Request(booking), "operator", ticket: qr.Read(Payload(booking))), 409);
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
    failBooking = (await reservations.GetByIdAsync(failBooking.Id))!;
    var failed = await service.CreateAsync(Request(failBooking), "operator", ticket: qr.Read(Payload(failBooking)));
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
    var multiStation = await stationService.AssignOperatorsAsync(station.Id, ["operator", "operator-two", "operator"]);
    Check(multiStation!.OperatorNICs.Count == 2, "duplicate operator assignments are normalized");
    Check(await stationAccess.CanAccessAsync(Context("operator-two", "Grid Operator").HttpContext.User, station.Id), "second assigned operator can access station");
    await stationService.AssignOperatorsAsync(station.Id, ["operator"]);
    Check(!await stationAccess.CanAccessAsync(Context("operator-two", "Grid Operator").HttpContext.User, station.Id), "removed operator loses station access");
    var createdStation = await stationService.CreateAsync(new SolarGridX.DTOs.Stations.CreateStationRequest { StationName = "Multi-operator station", OperatorNICs = ["operator", "operator-two"] });
    Check(createdStation.OperatorNICs.Count == 2, "new stations can assign multiple operators immediately");
    try { await stationService.AssignOperatorsAsync(station.Id, ["prosumer"]); throw new Exception("Expected invalid operator"); }
    catch (ArgumentException) { checks++; }
    Console.WriteLine($"Passed {checks} total checks, including MongoDB transactions, races, rollback, and capacity accounting.");
}
finally
{
    if (!dbName.StartsWith("sgx_transfer_", StringComparison.Ordinal)) throw new Exception("Invalid cleanup database");
    await client.DropDatabaseAsync(dbName);
    Console.WriteLine("Removed disposable transfer test database.");
}
