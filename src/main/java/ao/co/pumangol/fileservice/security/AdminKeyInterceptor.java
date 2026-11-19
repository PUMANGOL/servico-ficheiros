package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.config.FileServiceProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Protege a API de administração ({@code /api/v1/admin/**}) com o cabeçalho {@code X-Admin-Key}.
 * Sem chave configurada, a API de administração fica desligada.
 */
@Component
public class AdminKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER_ADMIN_KEY = "X-Admin-Key";
    private static final int MIN_KEY_LENGTH = 24;

    private static final Logger log = LoggerFactory.getLogger(AdminKeyInterceptor.class);

    private final String adminKey;

    public AdminKeyInterceptor(FileServiceProperties properties) {
        String configured = properties.security().adminKey();
        this.adminKey = (configured == null || configured.isBlank()) ? null : configured.trim();
        if (this.adminKey == null) {
            log.warn("fileservice.security.admin-key não está definida: a API de administração está desligada.");
        } else if (this.adminKey.length() < MIN_KEY_LENGTH) {
            throw new IllegalStateException("fileservice.security.admin-key (variável FILESERVICE_ADMIN_KEY) "
                    + "tem de ter pelo menos " + MIN_KEY_LENGTH + " caracteres aleatórios.");
        }
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        if (adminKey == null) {
            throw ApiException.forbidden("ADMIN_DISABLED", "A API de administração está desligada neste servidor.");
        }
        String presented = request.getHeader(HEADER_ADMIN_KEY);
        if (presented == null || !ApiKeys.secretsEqual(presented.trim(), adminKey)) {
            throw ApiException.unauthorized("Chave de administração em falta ou inválida (" + HEADER_ADMIN_KEY + ").");
        }
        return true;
    }
}
