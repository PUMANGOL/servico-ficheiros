package ao.co.pumangol.fileservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ApiKeysTest {

    @Test
    void generatedKeysAreUniqueAndPrefixed() {
        String first = ApiKeys.generate();
        String second = ApiKeys.generate();
        assertTrue(first.startsWith(ApiKeys.PREFIX));
        assertEquals(ApiKeys.PREFIX.length() + 43, first.length());
        assertNotEquals(first, second);
    }

    @Test
    void hashIsSha256Hex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApiKeys.hash("abc"));
    }

    @Test
    void matchesOnlyTheOriginalKey() {
        String key = ApiKeys.generate();
        String hash = ApiKeys.hash(key);
        assertTrue(ApiKeys.matches(key, hash));
        assertFalse(ApiKeys.matches(key + "x", hash));
        assertFalse(ApiKeys.matches(null, hash));
        assertFalse(ApiKeys.matches(key, null));
    }

    @Test
    void comparesPlainSecrets() {
        assertTrue(ApiKeys.secretsEqual("chave-admin", "chave-admin"));
        assertFalse(ApiKeys.secretsEqual("chave-admin", "chave-admim"));
        assertFalse(ApiKeys.secretsEqual(null, "chave-admin"));
    }

    @Test
    void visiblePrefixNeverRevealsTheWholeKey() {
        String key = ApiKeys.generate();
        assertEquals(12, ApiKeys.visiblePrefix(key).length());
        assertTrue(key.startsWith(ApiKeys.visiblePrefix(key)));
    }
}
