package ao.co.pumangol.fileservice.apps;

import java.util.List;

/**
 * Dados de registo/actualização de uma aplicação (corpo JSON dos endpoints de administração).
 *
 * @param code                 obrigatório na criação; ignorado na actualização (vem do URL)
 * @param name                 nome legível da aplicação
 * @param allowedOrigins       origens de browser autorizadas, ex.: {@code https://app.exemplo.co.ao}
 * @param maxFileSizeBytes     opcional; por omissão usa o limite global
 * @param allowedContentTypes  opcional; por omissão usa a lista global. Aceita {@code image/*}
 * @param storageQuotaBytes    opcional; por omissão não há quota
 * @param active               opcional; por omissão {@code true}
 */
public record AppDefinition(
        String code,
        String name,
        List<String> allowedOrigins,
        Long maxFileSizeBytes,
        List<String> allowedContentTypes,
        Long storageQuotaBytes,
        Boolean active) {
}
