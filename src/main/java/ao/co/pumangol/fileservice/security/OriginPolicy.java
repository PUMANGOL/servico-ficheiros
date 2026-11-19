package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.apps.RegisteredApp;
import ao.co.pumangol.fileservice.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsUtils;

/**
 * Um pedido vindo de um browser noutra origem só é aceite se essa origem estiver registada
 * na aplicação que o faz. Pedidos servidor-a-servidor (sem cabeçalho Origin) não são afectados.
 */
final class OriginPolicy {

    private OriginPolicy() {
    }

    static void requireAllowedOrigin(HttpServletRequest request, RegisteredApp app) {
        if (!CorsUtils.isCorsRequest(request)) {
            return;
        }
        String origin = Origins.normalize(request.getHeader(HttpHeaders.ORIGIN));
        if (!app.allowsOrigin(origin)) {
            throw ApiException.forbidden("ORIGIN_NOT_ALLOWED",
                    "A origem '" + origin + "' não está autorizada para a aplicação '" + app.code() + "'.");
        }
    }
}
