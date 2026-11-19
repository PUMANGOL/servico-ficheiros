package ao.co.pumangol.fileservice.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OriginsTest {

    @Test
    void normalizesCaseSpacesAndTrailingSlash() {
        assertEquals("https://app.exemplo.co.ao", Origins.normalize("  HTTPS://App.Exemplo.co.ao/ "));
        assertEquals("http://localhost:5173", Origins.normalize("http://localhost:5173"));
        assertNull(Origins.normalize("   "));
        assertNull(Origins.normalize(null));
    }

    @Test
    void acceptsSchemeHostAndOptionalPort() {
        assertTrue(Origins.isValid("https://app.exemplo.co.ao"));
        assertTrue(Origins.isValid("http://localhost:5173"));
        assertTrue(Origins.isValid("http://10.0.0.15:8080"));
        assertTrue(Origins.isValid("*"));
    }

    @Test
    void rejectsPathsWildcardsAndGarbage() {
        assertFalse(Origins.isValid("https://app.exemplo.co.ao/portal"));
        assertFalse(Origins.isValid("https://*.exemplo.co.ao"));
        assertFalse(Origins.isValid("app.exemplo.co.ao"));
        assertFalse(Origins.isValid("https://"));
        assertFalse(Origins.isValid(null));
    }
}
