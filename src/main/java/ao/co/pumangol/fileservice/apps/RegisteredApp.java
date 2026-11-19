package ao.co.pumangol.fileservice.apps;

import ao.co.pumangol.fileservice.security.Origins;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Aplicação cliente registada no serviço.
 *
 * @param code                 identificador público da aplicação (cabeçalho X-App-Id e pasta no disco)
 * @param apiKeyHash           SHA-256 da chave de API; a chave em claro nunca é guardada
 * @param maxFileSizeBytes     limite por ficheiro; {@code null} usa o valor por omissão do serviço
 * @param allowedContentTypes  tipos permitidos; lista vazia usa a lista por omissão do serviço
 * @param storageQuotaBytes    espaço total permitido; {@code null} significa sem quota
 * @param allowedOrigins       origens de browser autorizadas (CORS)
 */
public record RegisteredApp(
        UUID id,
        String code,
        String name,
        String apiKeyHash,
        String apiKeyPrefix,
        Long maxFileSizeBytes,
        List<String> allowedContentTypes,
        Long storageQuotaBytes,
        boolean active,
        Set<String> allowedOrigins,
        Instant createdAt,
        Instant updatedAt) {

    public RegisteredApp {
        allowedContentTypes = allowedContentTypes == null ? List.of() : List.copyOf(allowedContentTypes);
        allowedOrigins = allowedOrigins == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(allowedOrigins));
    }

    /** Recebe uma origem já normalizada (ver {@link Origins#normalize(String)}). */
    public boolean allowsOrigin(String normalizedOrigin) {
        if (normalizedOrigin == null) {
            return false;
        }
        return allowedOrigins.contains(Origins.ANY) || allowedOrigins.contains(normalizedOrigin);
    }

    public RegisteredApp withAllowedOrigins(Set<String> origins) {
        return new RegisteredApp(id, code, name, apiKeyHash, apiKeyPrefix, maxFileSizeBytes, allowedContentTypes,
                storageQuotaBytes, active, origins, createdAt, updatedAt);
    }

    public RegisteredApp withApiKey(String newHash, String newPrefix, Instant when) {
        return new RegisteredApp(id, code, name, newHash, newPrefix, maxFileSizeBytes, allowedContentTypes,
                storageQuotaBytes, active, allowedOrigins, createdAt, when);
    }
}
