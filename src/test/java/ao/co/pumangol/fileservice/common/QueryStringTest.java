package ao.co.pumangol.fileservice.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QueryStringTest {

    @Test
    void decodesNamesAndValues() {
        Map<String, String> params = QueryString.parse("app=portal&folder=facturas%2F2026&by=Jo%C3%A3o%20Zinga&exp=123");
        assertEquals("portal", params.get("app"));
        assertEquals("facturas/2026", params.get("folder"));
        assertEquals("João Zinga", params.get("by"));
        assertEquals("123", params.get("exp"));
    }

    @Test
    void firstValueWinsAndEmptyValuesAreKept() {
        Map<String, String> params = QueryString.parse("sig=primeiro&sig=segundo&vazio=&semvalor");
        assertEquals("primeiro", params.get("sig"));
        assertEquals("", params.get("vazio"));
        assertEquals("", params.get("semvalor"));
    }

    @Test
    void toleratesMissingOrMalformedInput() {
        assertTrue(QueryString.parse(null).isEmpty());
        assertTrue(QueryString.parse("").isEmpty());

        Map<String, String> params = QueryString.parse("ok=1&&mau=%ZZ&outro=2");
        assertEquals("1", params.get("ok"));
        assertEquals("2", params.get("outro"));
        assertFalse(params.containsKey("mau"));
    }
}
