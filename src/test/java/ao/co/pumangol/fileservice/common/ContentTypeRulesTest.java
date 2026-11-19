package ao.co.pumangol.fileservice.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContentTypeRulesTest {

    @Test
    void normalizesCaseAndParameters() {
        assertEquals("text/plain", ContentTypeRules.normalize(" Text/Plain; charset=UTF-8 "));
        assertEquals("", ContentTypeRules.normalize(null));
    }

    @Test
    void matchesExactTypesFamiliesAndWildcard() {
        assertTrue(ContentTypeRules.isAllowed("image/png", List.of("image/png")));
        assertTrue(ContentTypeRules.isAllowed("IMAGE/PNG", List.of("image/*")));
        assertTrue(ContentTypeRules.isAllowed("application/zip", List.of("*/*")));

        assertFalse(ContentTypeRules.isAllowed("image/svg+xml", List.of("image/png", "image/jpeg")));
        assertFalse(ContentTypeRules.isAllowed("text/html", List.of("image/*", "application/pdf")));
        assertFalse(ContentTypeRules.isAllowed("imagex/png", List.of("image/*")));
        assertFalse(ContentTypeRules.isAllowed("", List.of("*/*")));
        assertFalse(ContentTypeRules.isAllowed("image/png", List.of()));
    }

    @Test
    void defaultListAcceptsOfficeDocumentsAndRejectsExecutablesAndHtml() {
        List<String> allowed = ContentTypeRules.DEFAULT_ALLOWED;
        assertTrue(ContentTypeRules.isAllowed("application/pdf", allowed));
        assertTrue(ContentTypeRules.isAllowed(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", allowed));
        assertFalse(ContentTypeRules.isAllowed("text/html", allowed));
        assertFalse(ContentTypeRules.isAllowed("application/x-msdownload", allowed));
        assertFalse(ContentTypeRules.isAllowed("image/svg+xml", allowed));
    }

    @Test
    void validatesPatternSyntax() {
        assertTrue(ContentTypeRules.isValidPattern("image/png"));
        assertTrue(ContentTypeRules.isValidPattern("image/*"));
        assertTrue(ContentTypeRules.isValidPattern("*/*"));
        assertTrue(ContentTypeRules.isValidPattern("application/vnd.ms-excel"));

        assertFalse(ContentTypeRules.isValidPattern("image"));
        assertFalse(ContentTypeRules.isValidPattern("*/png"));
        assertFalse(ContentTypeRules.isValidPattern("image/png, image/jpeg"));
        assertFalse(ContentTypeRules.isValidPattern(null));
    }

    @Test
    void onlyHarmlessTypesAreShownInline() {
        assertTrue(ContentTypeRules.isInlineSafe("image/png"));
        assertTrue(ContentTypeRules.isInlineSafe("application/pdf"));
        assertFalse(ContentTypeRules.isInlineSafe("text/html"));
        assertFalse(ContentTypeRules.isInlineSafe("image/svg+xml"));
        assertFalse(ContentTypeRules.isInlineSafe("application/xhtml+xml"));
    }
}
