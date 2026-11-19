package ao.co.pumangol.fileservice.apps;

import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.common.ContentTypeRules;
import ao.co.pumangol.fileservice.security.ApiKeys;
import ao.co.pumangol.fileservice.security.Origins;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registo e autenticação das aplicações clientes.
 */
public class AppService {

    private static final Logger log = LoggerFactory.getLogger(AppService.class);

    /** Minúsculas, algarismos e hífenes; 2 a 50 caracteres. Também é o nome da pasta no disco. */
    private static final Pattern CODE = Pattern.compile("[a-z0-9]([a-z0-9-]{0,48}[a-z0-9])");
    private static final int MAX_NAME_LENGTH = 150;
    private static final int MAX_ORIGINS = 50;
    private static final int MAX_CONTENT_TYPES_LENGTH = 2000;

    /** Hash de comparação usado quando a aplicação não existe, para o tempo de resposta ser idêntico. */
    private static final String DUMMY_HASH = ApiKeys.hash("fileservice-dummy-key");

    private final AppRepository apps;
    private final Clock clock;

    public AppService(AppRepository apps, Clock clock) {
        this.apps = apps;
        this.clock = clock;
    }

    public CreatedApp create(AppDefinition definition) {
        requireBody(definition);
        String code = normalizeCode(definition.code());
        if (code == null || !CODE.matcher(code).matches()) {
            throw ApiException.badRequest("INVALID_APP_CODE",
                    "O campo 'code' é obrigatório: 2 a 50 caracteres, apenas minúsculas, algarismos e hífenes.");
        }
        if (apps.findByCode(code).isPresent()) {
            throw codeAlreadyExists(code);
        }

        String apiKey = ApiKeys.generate();
        Instant now = now();
        RegisteredApp app = new RegisteredApp(
                UUID.randomUUID(),
                code,
                validName(definition.name()),
                ApiKeys.hash(apiKey),
                ApiKeys.visiblePrefix(apiKey),
                validPositive(definition.maxFileSizeBytes(), "maxFileSizeBytes"),
                validContentTypes(definition.allowedContentTypes()),
                validPositive(definition.storageQuotaBytes(), "storageQuotaBytes"),
                definition.active() == null || definition.active(),
                validOrigins(definition.allowedOrigins()),
                now,
                now);
        apps.insert(app);
        log.info("Aplicação registada: code={} origins={}", app.code(), app.allowedOrigins());
        return new CreatedApp(app, apiKey);
    }

    public RegisteredApp update(String code, AppDefinition definition) {
        requireBody(definition);
        RegisteredApp current = get(code);
        RegisteredApp updated = new RegisteredApp(
                current.id(),
                current.code(),
                validName(definition.name()),
                current.apiKeyHash(),
                current.apiKeyPrefix(),
                validPositive(definition.maxFileSizeBytes(), "maxFileSizeBytes"),
                validContentTypes(definition.allowedContentTypes()),
                validPositive(definition.storageQuotaBytes(), "storageQuotaBytes"),
                definition.active() == null || definition.active(),
                validOrigins(definition.allowedOrigins()),
                current.createdAt(),
                now());
        apps.update(updated);
        log.info("Aplicação actualizada: code={} active={}", updated.code(), updated.active());
        return updated;
    }

    /** Gera uma chave nova; a anterior deixa de funcionar de imediato. */
    public CreatedApp rotateKey(String code) {
        RegisteredApp current = get(code);
        String apiKey = ApiKeys.generate();
        RegisteredApp updated = current.withApiKey(ApiKeys.hash(apiKey), ApiKeys.visiblePrefix(apiKey), now());
        apps.updateApiKey(updated.id(), updated.apiKeyHash(), updated.apiKeyPrefix(), updated.updatedAt());
        log.info("Chave de API rodada: code={}", updated.code());
        return new CreatedApp(updated, apiKey);
    }

    public RegisteredApp get(String code) {
        String normalized = normalizeCode(code);
        Optional<RegisteredApp> found = normalized == null ? Optional.empty() : apps.findByCode(normalized);
        return found.orElseThrow(() -> ApiException.notFound("Aplicação não encontrada: " + code));
    }

    public List<RegisteredApp> list() {
        return apps.findAll();
    }

    /**
     * Valida o par identificador + chave de API. A mensagem de erro é a mesma quer a aplicação
     * não exista quer a chave esteja errada, para não revelar que identificadores existem.
     */
    public RegisteredApp authenticate(String code, String apiKey) {
        String normalized = normalizeCode(code);
        Optional<RegisteredApp> found = (normalized != null && CODE.matcher(normalized).matches())
                ? apps.findByCode(normalized)
                : Optional.empty();

        String expectedHash = found.map(RegisteredApp::apiKeyHash).orElse(DUMMY_HASH);
        boolean keyMatches = ApiKeys.matches(apiKey, expectedHash);
        if (found.isEmpty() || !keyMatches) {
            throw ApiException.unauthorized("Identificador da aplicação ou chave de API inválidos.");
        }
        RegisteredApp app = found.get();
        if (!app.active()) {
            throw ApiException.forbidden("APP_DISABLED", "A aplicação está desactivada.");
        }
        return app;
    }

    // ---------------------------------------------------------------- validação

    private static void requireBody(AppDefinition definition) {
        if (definition == null) {
            throw ApiException.badRequest("INVALID_REQUEST", "O corpo do pedido é obrigatório.");
        }
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static String validName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > MAX_NAME_LENGTH) {
            throw ApiException.badRequest("INVALID_APP_NAME",
                    "O campo 'name' é obrigatório e tem no máximo " + MAX_NAME_LENGTH + " caracteres.");
        }
        return name.trim();
    }

    private static Long validPositive(Long value, String field) {
        if (value != null && value <= 0) {
            throw ApiException.badRequest("INVALID_LIMIT", "O campo '" + field + "' tem de ser maior que zero.");
        }
        return value;
    }

    private static Set<String> validOrigins(List<String> origins) {
        Set<String> result = new LinkedHashSet<>();
        if (origins == null) {
            return result;
        }
        for (String raw : origins) {
            String origin = Origins.normalize(raw);
            if (!Origins.isValid(origin)) {
                throw ApiException.badRequest("INVALID_ORIGIN",
                        "Origem inválida: '" + raw + "'. Formato esperado: https://anfitriao[:porta], sem caminho.");
            }
            result.add(origin);
        }
        if (result.size() > MAX_ORIGINS) {
            throw ApiException.badRequest("INVALID_ORIGIN", "No máximo " + MAX_ORIGINS + " origens por aplicação.");
        }
        return result;
    }

    private static List<String> validContentTypes(List<String> contentTypes) {
        List<String> result = new ArrayList<>();
        if (contentTypes == null) {
            return result;
        }
        for (String raw : contentTypes) {
            String pattern = ContentTypeRules.normalize(raw);
            if (!ContentTypeRules.isValidPattern(pattern)) {
                throw ApiException.badRequest("INVALID_CONTENT_TYPE",
                        "Tipo de conteúdo inválido: '" + raw + "'. Exemplos válidos: image/png, image/*.");
            }
            if (!result.contains(pattern)) {
                result.add(pattern);
            }
        }
        if (String.join(",", result).length() > MAX_CONTENT_TYPES_LENGTH) {
            throw ApiException.badRequest("INVALID_CONTENT_TYPE", "Lista de tipos de conteúdo demasiado longa.");
        }
        return result;
    }

    private static ApiException codeAlreadyExists(String code) {
        return ApiException.conflict("APP_CODE_EXISTS", "Já existe uma aplicação com o código '" + code + "'.");
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
