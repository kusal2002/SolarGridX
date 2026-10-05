// ============================================================================
// Project: SolarGridX - Smart Solar Microgrid Platform
// Module: Enterprise Application Development (EAD)
// File: AccountStatus.cs
// Description: Enum defining account status values for user lifecycle management.
// ============================================================================

namespace SolarGridX.Models
{
    public enum AccountStatus
    {
        Pending,
        Active,
        Inactive,
        DeactivationRequested
    }
}