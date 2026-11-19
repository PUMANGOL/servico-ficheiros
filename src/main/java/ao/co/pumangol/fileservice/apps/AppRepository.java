package ao.co.pumangol.fileservice.apps;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppRepository {

    /** Grava a aplicação e as suas origens. Lança {@code ApiException} 409 se o código já existir. */
    void insert(RegisteredApp app);

    /** Actualiza nome, limites, estado e origens (substituição integral). */
    void update(RegisteredApp app);

    void updateApiKey(UUID id, String apiKeyHash, String apiKeyPrefix, Instant updatedAt);

    Optional<RegisteredApp> findByCode(String code);

    Optional<RegisteredApp> findById(UUID id);

    List<RegisteredApp> findAll();

    /** Indica se alguma aplicação activa autoriza esta origem (usado no preflight CORS). */
    boolean isOriginAllowedByAnyActiveApp(String normalizedOrigin);
}
