package ao.co.pumangol.fileservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.common.QueryString;
import ao.co.pumangol.fileservice.files.Visibility;
import ao.co.pumangol.fileservice.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SignedUrlServiceTest {

    private static final String BASE_URL = "https://ficheiros.exemplo.co.ao";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
    private final SignedUrlService service = new SignedUrlService(
            new UrlSigner("segredo-de-teste-com-mais-de-32-caracteres!"),
            Duration.ofMinutes(5), Duration.ofHours(24), clock);

    @Test
    void downloadLinkIsValidUntilItExpires() {
        UUID fileId = UUID.randomUUID();
        SignedLink link = service.createDownloadLink(BASE_URL, fileId, 60L);
        Map<String, String> params = queryOf(link.url());
        long exp = Long.parseLong(params.get("exp"));

        assertTrue(link.url().startsWith(BASE_URL + "/api/v1/public/files/" + fileId + "?"));
        assertEquals(Instant.parse("2026-11-17T10:01:00Z"), link.expiresAt());
        assertTrue(service.isValidDownload(fileId, exp, params.get("sig")));

        clock.advance(Duration.ofSeconds(61));
        assertFalse(service.isValidDownload(fileId, exp, params.get("sig")));
    }

    @Test
    void downloadLinkCannotBeReusedForAnotherFileOrExtended() {
        UUID fileId = UUID.randomUUID();
        Map<String, String> params = queryOf(service.createDownloadLink(BASE_URL, fileId, null).url());
        long exp = Long.parseLong(params.get("exp"));

        assertFalse(service.isValidDownload(UUID.randomUUID(), exp, params.get("sig")));
        assertFalse(service.isValidDownload(fileId, exp + 3600, params.get("sig")));
    }

    @Test
    void defaultAndMaximumLifetimesAreEnforced() {
        UUID fileId = UUID.randomUUID();
        assertEquals(Instant.parse("2026-11-17T10:05:00Z"), service.createDownloadLink(BASE_URL, fileId, null).expiresAt());

        assertThrows(ApiException.class, () -> service.createDownloadLink(BASE_URL, fileId, 0L));
        assertThrows(ApiException.class, () -> service.createDownloadLink(BASE_URL, fileId, -5L));
        assertThrows(ApiException.class, () -> service.createDownloadLink(BASE_URL, fileId, 24 * 3600L + 1));
    }

    @Test
    void uploadLinkCarriesItsConditionsInTheSignature() {
        SignedLink link = service.createUploadLink(BASE_URL, "portal", "facturas/2026", Visibility.PRIVATE,
                "utilizador 42", 120L);
        Map<String, String> params = queryOf(link.url());
        long exp = Long.parseLong(params.get("exp"));
        String sig = params.get("sig");

        assertEquals("portal", params.get("app"));
        assertEquals("facturas/2026", params.get("folder"));
        assertEquals("utilizador 42", params.get("by"));
        assertEquals("PRIVATE", params.get("visibility"));

        assertTrue(service.isValidUpload("portal", "facturas/2026", Visibility.PRIVATE, "utilizador 42", exp, sig));
        assertFalse(service.isValidUpload("outra-app", "facturas/2026", Visibility.PRIVATE, "utilizador 42", exp, sig));
        assertFalse(service.isValidUpload("portal", "outra-pasta", Visibility.PRIVATE, "utilizador 42", exp, sig));
        assertFalse(service.isValidUpload("portal", "facturas/2026", Visibility.PUBLIC, "utilizador 42", exp, sig));
        assertFalse(service.isValidUpload("portal", "facturas/2026", Visibility.PRIVATE, "outro", exp, sig));

        clock.advance(Duration.ofSeconds(121));
        assertFalse(service.isValidUpload("portal", "facturas/2026", Visibility.PRIVATE, "utilizador 42", exp, sig));
    }

    @Test
    void uploadLinkWithoutOptionalFieldsOmitsThem() {
        SignedLink link = service.createUploadLink(BASE_URL, "portal", null, Visibility.PUBLIC, null, null);
        Map<String, String> params = queryOf(link.url());

        assertFalse(params.containsKey("folder"));
        assertFalse(params.containsKey("by"));
        assertTrue(service.isValidUpload("portal", null, Visibility.PUBLIC, null,
                Long.parseLong(params.get("exp")), params.get("sig")));
    }

    @Test
    void downloadSignatureIsNotAcceptedAsUploadSignature() {
        UUID fileId = UUID.randomUUID();
        Map<String, String> params = queryOf(service.createDownloadLink(BASE_URL, fileId, 60L).url());
        assertFalse(service.isValidUpload(fileId.toString(), null, Visibility.PRIVATE, null,
                Long.parseLong(params.get("exp")), params.get("sig")));
    }

    private static Map<String, String> queryOf(String url) {
        return QueryString.parse(url.substring(url.indexOf('?') + 1));
    }
}
