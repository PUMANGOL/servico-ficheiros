package ao.co.pumangol.fileservice.security;

import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.files.Visibility;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Criação e validação de links temporários.
 * <ul>
 *   <li><b>Download</b>: dá acesso de leitura a um ficheiro privado, sem credenciais.</li>
 *   <li><b>Upload</b>: permite ao browser enviar ficheiros em nome de uma aplicação, com a
 *       pasta, a visibilidade e o autor fixados pelo backend que pediu o link.</li>
 * </ul>
 * Um link vale até expirar; não é de utilização única.
 */
public class SignedUrlService {

    public static final String PARAM_EXPIRES = "exp";
    public static final String PARAM_SIGNATURE = "sig";
    public static final String PARAM_APP = "app";
    public static final String PARAM_FOLDER = "folder";
    public static final String PARAM_VISIBILITY = "visibility";
    public static final String PARAM_UPLOADED_BY = "by";

    private static final String PURPOSE_DOWNLOAD = "download-v1";
    private static final String PURPOSE_UPLOAD = "upload-v1";

    private final UrlSigner signer;
    private final Duration defaultTtl;
    private final Duration maxTtl;
    private final Clock clock;

    public SignedUrlService(UrlSigner signer, Duration defaultTtl, Duration maxTtl, Clock clock) {
        if (defaultTtl.isNegative() || defaultTtl.isZero() || maxTtl.compareTo(defaultTtl) < 0) {
            throw new IllegalStateException(
                    "Configuração inválida em fileservice.signed-urls: default-ttl tem de ser positivo e não superior a max-ttl.");
        }
        this.signer = signer;
        this.defaultTtl = defaultTtl;
        this.maxTtl = maxTtl;
        this.clock = clock;
    }

    public SignedLink createDownloadLink(String baseUrl, UUID fileId, Long ttlSeconds) {
        long exp = expiryEpochSecond(ttlSeconds);
        String signature = signer.sign(PURPOSE_DOWNLOAD, fileId.toString(), Long.toString(exp));
        String url = baseUrl + "/api/v1/public/files/" + fileId
                + "?" + PARAM_EXPIRES + "=" + exp
                + "&" + PARAM_SIGNATURE + "=" + signature;
        return new SignedLink(url, Instant.ofEpochSecond(exp));
    }

    public boolean isValidDownload(UUID fileId, long exp, String signature) {
        return notExpired(exp) && signer.matches(signature, PURPOSE_DOWNLOAD, fileId.toString(), Long.toString(exp));
    }

    public SignedLink createUploadLink(String baseUrl, String appCode, String folder, Visibility visibility,
                                       String uploadedBy, Long ttlSeconds) {
        long exp = expiryEpochSecond(ttlSeconds);
        String folderValue = nullToEmpty(folder);
        String byValue = nullToEmpty(uploadedBy);
        String signature = signer.sign(PURPOSE_UPLOAD, appCode, folderValue, visibility.name(), byValue, Long.toString(exp));

        StringBuilder url = new StringBuilder(baseUrl).append("/api/v1/public/uploads")
                .append('?').append(PARAM_APP).append('=').append(encode(appCode))
                .append('&').append(PARAM_EXPIRES).append('=').append(exp)
                .append('&').append(PARAM_VISIBILITY).append('=').append(visibility.name());
        if (!folderValue.isEmpty()) {
            url.append('&').append(PARAM_FOLDER).append('=').append(encode(folderValue));
        }
        if (!byValue.isEmpty()) {
            url.append('&').append(PARAM_UPLOADED_BY).append('=').append(encode(byValue));
        }
        url.append('&').append(PARAM_SIGNATURE).append('=').append(signature);
        return new SignedLink(url.toString(), Instant.ofEpochSecond(exp));
    }

    public boolean isValidUpload(String appCode, String folder, Visibility visibility, String uploadedBy,
                                 long exp, String signature) {
        return notExpired(exp) && signer.matches(signature, PURPOSE_UPLOAD, appCode, nullToEmpty(folder),
                visibility.name(), nullToEmpty(uploadedBy), Long.toString(exp));
    }

    private boolean notExpired(long exp) {
        return exp > clock.instant().getEpochSecond();
    }

    private long expiryEpochSecond(Long ttlSeconds) {
        Duration ttl = ttlSeconds == null ? defaultTtl : Duration.ofSeconds(ttlSeconds);
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(maxTtl) > 0) {
            throw ApiException.badRequest("INVALID_EXPIRATION",
                    "expiresInSeconds tem de estar entre 1 e " + maxTtl.toSeconds() + " segundos.");
        }
        return clock.instant().plus(ttl).getEpochSecond();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String encode(String value) {
        // URLEncoder usa '+' para espaços (formato de formulário); num URL preferimos %20.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
