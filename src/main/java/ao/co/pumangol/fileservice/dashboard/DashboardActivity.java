package ao.co.pumangol.fileservice.dashboard;

import java.time.Instant;
import java.util.UUID;

public record DashboardActivity(
        UUID id,
        String eventType,
        String applicationCode,
        String resourceId,
        String details,
        Instant occurredAt) {
}
