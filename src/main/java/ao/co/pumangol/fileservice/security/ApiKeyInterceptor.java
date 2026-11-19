package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.apps.AppService;
import ao.co.pumangol.fileservice.apps.RegisteredApp;
import ao.co.pumangol.fileservice.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Autenticação das aplicações: cabeçalhos {@code X-App-Id} e {@code X-Api-Key}.
 * A aplicação autenticada fica disponível no pedido em {@link AuthAttributes#APP}.
 */
@Component
public class ApiKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER_APP_ID = "X-App-Id";
    public static final String HEADER_API_KEY = "X-Api-Key";

    private final AppService appService;

    public ApiKeyInterceptor(AppService appService) {
        this.appService = appService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        String appId = request.getHeader(HEADER_APP_ID);
        String apiKey = request.getHeader(HEADER_API_KEY);
        if (appId == null || appId.isBlank() || apiKey == null || apiKey.isBlank()) {
            throw ApiException.unauthorized(
                    "Credenciais em falta: envie os cabeçalhos " + HEADER_APP_ID + " e " + HEADER_API_KEY + ".");
        }
        RegisteredApp app = appService.authenticate(appId, apiKey.trim());
        OriginPolicy.requireAllowedOrigin(request, app);
        request.setAttribute(AuthAttributes.APP, app);
        return true;
    }
}
