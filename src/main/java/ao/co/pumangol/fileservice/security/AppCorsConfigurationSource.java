package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.apps.AppRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;

/**
 * CORS dinâmico: uma origem é aceite se estiver registada em pelo menos uma aplicação activa.
 * <p>
 * O preflight (OPTIONS) não traz credenciais, por isso aqui só se sabe a origem. A verificação
 * fina — a origem pertence à aplicação que faz o pedido — é feita depois, na autenticação
 * (ver {@link OriginPolicy}).
 */
@Component
public class AppCorsConfigurationSource implements CorsConfigurationSource {

    private static final String API_PREFIX = "/api/";

    private static final List<String> ALLOWED_METHODS = List.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            ApiKeyInterceptor.HEADER_APP_ID,
            ApiKeyInterceptor.HEADER_API_KEY,
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.ACCEPT,
            HttpHeaders.RANGE,
            HttpHeaders.IF_NONE_MATCH);
    private static final List<String> EXPOSED_HEADERS = List.of(
            HttpHeaders.CONTENT_DISPOSITION,
            HttpHeaders.CONTENT_LENGTH,
            HttpHeaders.CONTENT_RANGE,
            HttpHeaders.ACCEPT_RANGES,
            HttpHeaders.ETAG,
            HttpHeaders.LOCATION);

    private final AppRepository apps;

    public AppCorsConfigurationSource(AppRepository apps) {
        this.apps = apps;
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        String origin = Origins.normalize(request.getHeader(HttpHeaders.ORIGIN));
        if (origin == null || !isApiRequest(request) || !Origins.isValid(origin)) {
            return null;
        }
        if (!apps.isOriginAllowedByAnyActiveApp(origin)) {
            if (CorsUtils.isPreFlightRequest(request)) {
                CorsConfiguration rejected = new CorsConfiguration();
                rejected.setAllowedOrigins(List.of());
                return rejected;
            }
            return null;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(origin));
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setExposedHeaders(EXPOSED_HEADERS);
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        return config;
    }

    private static boolean isApiRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith(API_PREFIX);
    }
}
