package ao.co.pumangol.fileservice.config;

import ao.co.pumangol.fileservice.security.AdminKeyInterceptor;
import ao.co.pumangol.fileservice.security.ApiKeyInterceptor;
import ao.co.pumangol.fileservice.security.AppCorsConfigurationSource;
import ao.co.pumangol.fileservice.security.SignedUploadInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mapa de segurança da API:
 * <pre>
 *   /api/v1/admin/**          X-Admin-Key                     (gestão das aplicações)
 *   /api/v1/public/uploads    link assinado                   (upload feito pelo browser)
 *   /api/v1/public/files/**   ficheiro PUBLIC ou link assinado (verificado no serviço)
 *   /api/v1/**  (restante)    X-App-Id + X-Api-Key            (tudo o resto, por omissão)
 * </pre>
 * Qualquer endpoint novo debaixo de {@code /api/v1} fica protegido por chave de API sem
 * ser preciso configurar nada.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminKeyInterceptor adminKeyInterceptor;
    private final ApiKeyInterceptor apiKeyInterceptor;
    private final SignedUploadInterceptor signedUploadInterceptor;

    public WebConfig(AdminKeyInterceptor adminKeyInterceptor, ApiKeyInterceptor apiKeyInterceptor,
                     SignedUploadInterceptor signedUploadInterceptor) {
        this.adminKeyInterceptor = adminKeyInterceptor;
        this.apiKeyInterceptor = apiKeyInterceptor;
        this.signedUploadInterceptor = signedUploadInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminKeyInterceptor)
                .addPathPatterns("/api/v1/admin/**");
        registry.addInterceptor(signedUploadInterceptor)
                .addPathPatterns("/api/v1/public/uploads");
        registry.addInterceptor(apiKeyInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/admin/**", "/api/v1/public/**");
    }

    /**
     * Filtro CORS ao nível do servlet: responde aos preflight e acrescenta os cabeçalhos CORS
     * também às respostas de erro (401, 403, 413...), para o frontend conseguir lê-las.
     */
    @Bean
    public CorsFilter corsFilter(AppCorsConfigurationSource corsConfigurationSource) {
        return new CorsFilter(corsConfigurationSource);
    }
}
