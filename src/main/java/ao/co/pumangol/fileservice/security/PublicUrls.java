package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.config.FileServiceProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Constrói os URLs absolutos devolvidos aos clientes. Usa {@code fileservice.public-base-url}
 * quando definido; caso contrário deriva o endereço do pedido em curso (respeitando os
 * cabeçalhos X-Forwarded-* do proxy).
 */
@Component
public class PublicUrls {

    private final String configuredBaseUrl;

    public PublicUrls(FileServiceProperties properties) {
        this.configuredBaseUrl = stripTrailingSlashes(properties.publicBaseUrl());
    }

    public String baseUrl() {
        if (configuredBaseUrl != null) {
            return configuredBaseUrl;
        }
        return stripTrailingSlashes(ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString());
    }

    public String fileMetadataUrl(UUID fileId) {
        return baseUrl() + "/api/v1/files/" + fileId;
    }

    public String publicFileUrl(UUID fileId) {
        return baseUrl() + "/api/v1/public/files/" + fileId;
    }

    private static String stripTrailingSlashes(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
