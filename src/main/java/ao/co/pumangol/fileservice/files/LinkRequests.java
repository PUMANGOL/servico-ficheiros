package ao.co.pumangol.fileservice.files;

import java.time.Instant;

/** Corpos JSON dos endpoints de links temporários. */
public final class LinkRequests {

    private LinkRequests() {
    }

    /**
     * Pedido de link de download.
     *
     * @param expiresInSeconds validade em segundos; opcional (usa o valor por omissão do serviço)
     */
    public record DownloadLinkRequest(Long expiresInSeconds) {
    }

    /**
     * Pedido de link de upload. As condições ficam fixadas na assinatura: quem usa o link
     * não as consegue alterar.
     *
     * @param folder           pasta lógica onde os ficheiros ficam; opcional
     * @param visibility       PRIVATE (por omissão) ou PUBLIC
     * @param uploadedBy       referência ao utilizador final na aplicação cliente; opcional
     * @param expiresInSeconds validade em segundos; opcional
     */
    public record UploadLinkRequest(String folder, String visibility, String uploadedBy, Long expiresInSeconds) {
    }

    public record LinkResponse(String url, Instant expiresAt) {
    }
}
