package ao.co.pumangol.fileservice.apps;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Representação de uma aplicação na API de administração. Nunca inclui a chave nem o seu hash. */
public record AppResponse(
        UUID id,
        String code,
        String name,
        boolean active,
        List<String> allowedOrigins,
        Long maxFileSizeBytes,
        List<String> allowedContentTypes,
        Long storageQuotaBytes,
        String apiKeyPrefix,
        Instant createdAt,
        Instant updatedAt) {

    public static AppResponse from(RegisteredApp app) {
        return new AppResponse(
                app.id(),
                app.code(),
                app.name(),
                app.active(),
                List.copyOf(app.allowedOrigins()),
                app.maxFileSizeBytes(),
                app.allowedContentTypes(),
                app.storageQuotaBytes(),
                app.apiKeyPrefix(),
                app.createdAt(),
                app.updatedAt());
    }
}
