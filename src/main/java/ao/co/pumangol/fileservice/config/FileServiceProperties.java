package ao.co.pumangol.fileservice.config;

import ao.co.pumangol.fileservice.common.ContentTypeRules;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Configuração do serviço (prefixo {@code fileservice} em application.yml).
 *
 * @param publicBaseUrl URL público do serviço, usado para construir links; vazio = derivado do pedido
 */
@ConfigurationProperties(prefix = "fileservice")
public record FileServiceProperties(
        String publicBaseUrl,
        @DefaultValue Storage storage,
        @DefaultValue Security security,
        @DefaultValue Limits limits,
        @DefaultValue SignedUrls signedUrls) {

    /** @param basePath pasta raiz onde os ficheiros são guardados */
    public record Storage(@DefaultValue("./data/storage") String basePath) {
    }

    /**
     * @param adminKey      chave do cabeçalho X-Admin-Key; vazia desliga a API de administração
     * @param signingSecret segredo HMAC dos links temporários (mínimo 32 caracteres)
     */
    public record Security(String adminKey, String signingSecret) {
    }

    /**
     * @param maxFileSize         tamanho máximo por ficheiro, por omissão
     * @param allowedContentTypes tipos permitidos por omissão
     */
    public record Limits(@DefaultValue("20MB") DataSize maxFileSize, List<String> allowedContentTypes) {

        public Limits {
            allowedContentTypes = (allowedContentTypes == null || allowedContentTypes.isEmpty())
                    ? ContentTypeRules.DEFAULT_ALLOWED
                    : List.copyOf(allowedContentTypes);
        }
    }

    /**
     * @param defaultTtl validade de um link quando o pedido não indica outra
     * @param maxTtl     validade máxima que uma aplicação pode pedir
     */
    public record SignedUrls(@DefaultValue("5m") Duration defaultTtl, @DefaultValue("24h") Duration maxTtl) {
    }
}
