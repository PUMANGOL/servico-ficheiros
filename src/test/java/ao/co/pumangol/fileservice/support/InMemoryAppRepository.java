package ao.co.pumangol.fileservice.support;

import ao.co.pumangol.fileservice.apps.AppRepository;
import ao.co.pumangol.fileservice.apps.RegisteredApp;
import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.security.Origins;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Repositório em memória para testar as regras de negócio sem base de dados. */
public class InMemoryAppRepository implements AppRepository {

    private final Map<UUID, RegisteredApp> apps = new LinkedHashMap<>();

    @Override
    public void insert(RegisteredApp app) {
        if (findByCode(app.code()).isPresent()) {
            throw ApiException.conflict("APP_CODE_EXISTS", "Já existe uma aplicação com o código '" + app.code() + "'.");
        }
        apps.put(app.id(), app);
    }

    @Override
    public void update(RegisteredApp app) {
        apps.put(app.id(), app);
    }

    @Override
    public void updateApiKey(UUID id, String apiKeyHash, String apiKeyPrefix, Instant updatedAt) {
        apps.computeIfPresent(id, (key, app) -> app.withApiKey(apiKeyHash, apiKeyPrefix, updatedAt));
    }

    @Override
    public Optional<RegisteredApp> findByCode(String code) {
        return apps.values().stream().filter(app -> app.code().equals(code)).findFirst();
    }

    @Override
    public Optional<RegisteredApp> findById(UUID id) {
        return Optional.ofNullable(apps.get(id));
    }

    @Override
    public List<RegisteredApp> findAll() {
        return apps.values().stream().sorted(Comparator.comparing(RegisteredApp::code)).toList();
    }

    @Override
    public boolean isOriginAllowedByAnyActiveApp(String normalizedOrigin) {
        return apps.values().stream()
                .filter(RegisteredApp::active)
                .anyMatch(app -> app.allowedOrigins().contains(normalizedOrigin)
                        || app.allowedOrigins().contains(Origins.ANY));
    }
}
