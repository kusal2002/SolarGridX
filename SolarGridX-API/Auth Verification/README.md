# Authentication review and Bruno testing

Reviewed 2026-09-29. Verdict: the core identity API is substantially implemented, but application-wide authorization is incomplete.

## Review findings

1. **High: business APIs are publicly accessible in source.** StationController, SlotController, ReservationController and EnergyTransfersController have no authorization attributes, and Program.cs has no fallback authorization policy. Anonymous callers can reach mutations as well as reads. Reservation operations also do not bind the requested account to the authenticated identity. Add role policies and reservation ownership checks according to the team's agreed role matrix.
2. **Medium: duplicate email races.** AuthService checks for an existing email before insert/update, but the repository creates no unique email index. Two concurrent requests can pass the check. MongoDB's NIC _id already enforces NIC uniqueness, but duplicate-key insert errors are not translated to HTTP 409. Verify deployed indexes, add a unique normalized-email index and handle duplicate-key failures.
3. **Medium: legacy login can overwrite concurrent changes.** LoginUserAsync replaces the entire user document when SecurityStamp is empty. A concurrent deactivation/profile update can be overwritten by that stale document. Use an atomic conditional stamp update instead.
4. **Lower priority: invalid Location header.** Registration/staff creation return /api/auth/users/{nic}, but no GET endpoint exists at that URL.
5. **Existing Bruno status request needs correction.** Tudakshana/Identity/Backoffice Update Status.yml uses YAML data: "Active", which decodes to the unquoted text Active. The API requires the JSON string "Active". This new collection preserves those JSON quotes with a YAML block scalar.

## Implemented correctly in source

- Public registration always creates a Pending Prosumer; clients cannot choose an administrator role.
- Passwords are BCrypt hashed; response DTOs exclude the hash and security stamp.
- Login normalizes email, verifies the password and only accepts Active accounts.
- JWT validation checks signing key, issuer, audience and expiry.
- Each authenticated request checks current account status and security stamp.
- Backoffice alone can list accounts, create staff and change account status.
- Profile editing checks account ownership, with a Backoffice override.
- Only a Prosumer can request their own deactivation.
- Status changes rotate the stamp, so reactivation does not restore old tokens.

## Scope and verification

The full linked ChatGPT conversation could not be retrieved; the preview is not the original assignment specification. Exact rubric compliance is therefore unverified. Login currently accepts email/password only. DeactivationRequested immediately blocks login and authenticated requests; confirm whether the assignment expects this or access until approval. Web App.tsx currently renders the stations UI directly without a login gate.

The current source builds with zero warnings/errors using:

```powershell
dotnet build SolarGridX/SolarGridX.csproj --no-restore -p:UseAppHost=false -o SolarGridX/bin/AuthReview
```

The normal build encountered an executable locked by an existing process, so a separate build output was used. Read-only localhost HTTP/HTTPS probes failed during TLS setup; no live API behavior or database indexes were verified. The collection below is prepared for manual execution, not claimed as passed. No backend implementation was changed.

## Open in Bruno

Open the existing SolarGridX-API directory as a collection and select **Auth Verification**. These YAML requests use the same OpenCollection format as the existing requests.

Configure MongoDB and JWT using local secrets (see the repository README). Required configuration keys are MongoDbSettings:ConnectionString, Jwt:Key (at least 32 UTF-8 bytes), Jwt:Issuer and Jwt:Audience. Issuer and audience already have defaults in appsettings.json.

The initial administrator is created at startup only when all BootstrapAdmin:NIC, BootstrapAdmin:Name, BootstrapAdmin:Email and BootstrapAdmin:Password values are configured and the NIC/email does not already exist. Public registration cannot create an administrator.

For the HTTP launch profile:

```powershell
dotnet run --project SolarGridX/SolarGridX.csproj --launch-profile http
```

If HTTP redirects to HTTPS, use the configured HTTPS profile and trust the local development certificate. Do not start a second instance on an occupied port.

Create an active Bruno environment with these variables. Keep real passwords and tokens local, and use disposable test users.

| Variable | Example / value |
|---|---|
| baseUrl | http://localhost:5084 (no trailing slash); HTTPS profile: https://localhost:7172 |
| backofficeEmail | Your configured bootstrap administrator email |
| backofficePassword | Your configured bootstrap administrator password |
| backofficeToken | Initially empty; paste token from request 03 |
| prosumerNic | 200012345678 (use a fresh unused NIC for each full run) |
| prosumerEmail | bruno.prosumer.01@example.com (use a fresh email) |
| prosumerPassword | TestPassword123! |
| prosumerToken | Initially empty; paste token from request 06, replace after request 22 |
| otherUserNic | A different NIC, such as 200098765432 |
| operatorNic | 199912345678 (unused NIC) |
| operatorEmail | bruno.operator.01@example.com (unused email) |
| operatorPassword | TestPassword123! |
| operatorToken | Initially empty; paste token from request 13 |

Run requests one at a time in numeric order. After successful logins 03, 06, 13 and 22, copy the response's **token** into the corresponding variable; use the raw token without the Bearer prefix. The requests configure bearer authentication for you. These are manual checks, with expected statuses in request titles; there are no automatic assertions or token capture scripts.

Registration and staff creation persist users. Deactivation and reactivation change the disposable Prosumer's status. Use fresh NICs/emails when repeating the full sequence; repeat registration otherwise returns 409.

## Ordered checks

| # | Request | Expected status |
|---|---|---|
| 01 | Register Prosumer | undefined |
| 02 | Pending Login Rejected | undefined |
| 03 | Login Backoffice | undefined |
| 04 | List Pending Users | undefined |
| 05 | Activate Prosumer | undefined |
| 06 | Login Prosumer | undefined |
| 07 | Get My Profile | undefined |
| 08 | Update My Profile | undefined |
| 09 | No Token Rejected | undefined |
| 10 | Prosumer Admin Access Rejected | undefined |
| 11 | Cross Account Update Rejected | undefined |
| 12 | Create Grid Operator | undefined |
| 13 | Login Grid Operator | undefined |
| 14 | Operator Admin Access Rejected | undefined |
| 15 | Request Deactivation | undefined |
| 16 | Revoked Token Rejected | undefined |
| 17 | Deactivation Requested Login Rejected | undefined |
| 18 | Approve Deactivation | undefined |
| 19 | Inactive Login Rejected | undefined |
| 20 | Reactivate Prosumer | undefined |
| 21 | Old Token Still Rejected | undefined |
| 22 | Login After Reactivation | undefined |
| 23 | Profile After Reactivation | undefined |

Also check response content: registration returns role Prosumer/status Pending; activation returns Active; staff creation returns Grid Operator/Active; login returns a nonempty token; profile responses never expose passwordHash or securityStamp. Request 15 returns DeactivationRequested and immediately invalidates the Prosumer token. Keep the old token through request 21 to verify it remains invalid after reactivation.

## Endpoint reference

All paths below use the /api/auth prefix.

| Method | Path | Access | Body |
|---|---|---|---|
| POST | /register | Public | nic, name, email, password |
| POST | /login | Public | email, password |
| GET | /me | Any active authenticated user | None |
| GET | /users?status=Pending&role=Prosumer | Backoffice | None; filters optional |
| POST | /staff | Backoffice | nic, name, email, password, role |
| PATCH | /users/{nic}/status | Backoffice | JSON string: "Active", "Inactive" or "DeactivationRequested" |
| PUT | /users/{nic}/profile | Owner or Backoffice | name, email |
| POST | /users/{nic}/deactivation-request | Prosumer owner | None |

Staff role must be exactly **Backoffice** or **Grid Operator**. Registration/staff passwords require at least eight characters. Status values are case sensitive; Pending is created by registration and is not accepted by the status update endpoint.

Example status body (JSON, not an object):

```json
"Active"
```

Additional manual negative checks: repeat registration (409); use a wrong password (401); omit required fields (400); create staff with role Prosumer (400); use status Pending or an object such as {"status":"Active"} (400); update profile to another existing user's email (409).

