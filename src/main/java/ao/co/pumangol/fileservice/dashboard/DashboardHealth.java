package ao.co.pumangol.fileservice.dashboard;

import java.time.Instant;

public record DashboardHealth(
        String status,
        Instant checkedAt,
        long uptimeSeconds,
        String databaseStatus,
        String storageStatus) {
}
