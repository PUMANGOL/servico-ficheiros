package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.common.QueryString;
import ao.co.pumangol.fileservice.files.FileService;
import ao.co.pumangol.fileservice.files.UploadGrant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Valida o link assinado de upload ({@code /api/v1/public/uploads}) antes de o corpo do
 * pedido ser lido. Os parâmetros vêm da query string em bruto para não forçar a leitura
 * do multipart. A autorização fica no pedido em {@link AuthAttributes#UPLOAD_GRANT}.
 */
@Component
public class SignedUploadInterceptor implements HandlerInterceptor {

    private final FileService fileService;

    public SignedUploadInterceptor(FileService fileService) {
        this.fileService = fileService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        UploadGrant grant = fileService.authorizeSignedUpload(QueryString.parse(request.getQueryString()));
        OriginPolicy.requireAllowedOrigin(request, grant.app());
        request.setAttribute(AuthAttributes.UPLOAD_GRANT, grant);
        return true;
    }
}
