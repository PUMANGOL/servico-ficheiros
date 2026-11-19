package ao.co.pumangol.fileservice.dashboard;

import java.time.Instant;
import java.util.UUID;

public record DashboardFile(
        UUID id,
        String applicationCode,
        String applicationName,
        String filename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        String visibility,
        String folder,
        String uploadedBy,
        Instant createdAt) {
}
