package ao.co.pumangol.fileservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UrlSignerTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres!";

    private final UrlSigner signer = new UrlSigner(SECRET);

    @Test
    void sameInputGivesSameSignature() {
        assertEquals(signer.sign("download", "abc", "123"), signer.sign("download", "abc", "123"));
    }

    @Test
    void signatureIsUrlSafe() {
        String signature = signer.sign("download", "abc", "123");
        assertTrue(signature.matches("[A-Za-z0-9_-]{43}"), signature);
    }

    @Test
    void acceptsOnlyTheExactFields() {
        String signature = signer.sign("download", "abc", "123");
        assertTrue(signer.matches(signature, "download", "abc", "123"));
        assertFalse(signer.matches(signature, "download", "abc", "124"));
        assertFalse(signer.matches(signature, "upload", "abc", "123"));
        assertFalse(signer.matches(signature + "x", "download", "abc", "123"));
        assertFalse(signer.matches("", "download", "abc", "123"));
        assertFalse(signer.matches(null, "download", "abc", "123"));
    }

    @Test
    void fieldBoundariesAreNotAmbiguous() {
        assertNotEquals(signer.sign("ab", "c"), signer.sign("a", "bc"));
        assertNotEquals(signer.sign("a", ""), signer.sign("", "a"));
    }

    @Test
    void differentSecretsGiveDifferentSignatures() {
        UrlSigner other = new UrlSigner("outro-segredo-com-mais-de-32-caracteres-xyz");
        assertNotEquals(signer.sign("download", "abc"), other.sign("download", "abc"));
    }

    @Test
    void rejectsMissingOrShortSecret() {
        assertThrows(IllegalStateException.class, () -> new UrlSigner(null));
        assertThrows(IllegalStateException.class, () -> new UrlSigner(""));
        assertThrows(IllegalStateException.class, () -> new UrlSigner("curto"));
    }
}
