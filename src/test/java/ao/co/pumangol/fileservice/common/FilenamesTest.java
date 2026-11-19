package ao.co.pumangol.fileservice.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FilenamesTest {

    @Test
    void keepsOrdinaryNamesIncludingAccents() {
        assertEquals("Relatório de Gestão 2026.pdf", Filenames.sanitize("Relatório de Gestão 2026.pdf"));
    }

    @Test
    void dropsDirectoriesSentByTheClient() {
        assertEquals("foto.png", Filenames.sanitize("C:\\Users\\zinga\\Desktop\\foto.png"));
        assertEquals("passwd", Filenames.sanitize("../../../etc/passwd"));
        assertEquals("foto.png", Filenames.sanitize("/tmp/uploads/foto.png"));
    }

    @Test
    void replacesControlAndReservedCharacters() {
        assertEquals("a_b_c_.txt", Filenames.sanitize("a\u0000b\nc?.txt"));
        assertEquals("nome_com_aspas_.txt", Filenames.sanitize("nome\"com\"aspas*.txt"));
    }

    @Test
    void fallsBackToDefaultNameWhenNothingIsLeft() {
        assertEquals(Filenames.DEFAULT_NAME, Filenames.sanitize(null));
        assertEquals(Filenames.DEFAULT_NAME, Filenames.sanitize("   "));
        assertEquals(Filenames.DEFAULT_NAME, Filenames.sanitize(".."));
        assertEquals(Filenames.DEFAULT_NAME, Filenames.sanitize("pasta/"));
    }

    @Test
    void truncatesLongNamesKeepingTheExtension() {
        String sanitized = Filenames.sanitize("a".repeat(400) + ".pdf");
        assertEquals(Filenames.MAX_LENGTH, sanitized.length());
        assertTrue(sanitized.endsWith(".pdf"));
    }
}
