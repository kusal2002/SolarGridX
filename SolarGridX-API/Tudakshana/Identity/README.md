# JWT and role-based identity tests

Run the API with MongoDB available and configure the JWT key first:

```bash
dotnet user-secrets set "Jwt:Key" "your-long-random-secret" --project SolarGridX/SolarGridX.csproj
dotnet run --project SolarGridX/SolarGridX.csproj --launch-profile http
```

Use these Bruno variables in the active environment:

```text
backofficeEmail    = the seeded Backoffice email
backofficePassword = the seeded Backoffice password
prosumerEmail      = an Active Prosumer email
prosumerPassword   = the Prosumer password
prosumerNic        = the Active Prosumer NIC
otherUserNic       = a different Prosumer NIC
backofficeToken    = JWT returned by Test Login - Backoffice JWT
prosumerToken      = JWT returned by Test Login - Prosumer JWT
```

## Test order and expected results

| Request | Expected status | What it verifies |
|---|---:|---|
| Test Login - Backoffice JWT | `200` | Valid credentials return a JWT with the `Backoffice` role claim. |
| Test Login - Prosumer JWT | `200` | Valid credentials return a JWT with the `Prosumer` role claim. |
| Test Unauthorized Request | `401` | Protected endpoints reject requests without a bearer token. |
| Test Backoffice Can Manage Users | `200` | A Backoffice JWT can list users. |
| Test Prosumer Cannot Manage Users | `403` | A Prosumer JWT cannot access Backoffice user management. |
| Test Profile Ownership | `403` | A Prosumer cannot update another user's profile. |
| Test JWT Revocation After Deactivation | `200` | A Prosumer can request self-deactivation. |
| Test Revoked JWT Is Rejected | `401` | The same token is rejected immediately after status change. |

The login requests return the token in the `token` response property. Copy each token into its matching Bruno bearer-token variable before running protected requests. The deactivation test changes the account status, so use a disposable Prosumer account and create a new active account before repeating the sequence.
