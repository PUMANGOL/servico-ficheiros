package ao.co.pumangol.fileservice.support;

import ao.co.pumangol.fileservice.storage.ContentTypeDetector;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Detector mínimo para os testes de negócio (o real é o Tika, exercitado no teste de integração).
 * Olha para os primeiros bytes, tal como o detector real, e ignora a extensão quando o
 * conteúdo é reconhecido.
 */
public class SimpleContentTypeDetector implements ContentTypeDetector {

    public static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Override
    public String detect(Path file, String filename) {
        byte[] head;
        try (InputStream in = Files.newInputStream(file)) {
            head = in.readNBytes(64);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (startsWith(head, PNG_HEADER)) {
            return "image/png";
        }
        String text = new String(head, StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        if (text.startsWith("%pdf-")) {
            return "application/pdf";
        }
        if (text.contains("<html") || text.contains("<script")) {
            return "text/html";
        }
        return filename.toLowerCase(Locale.ROOT).endsWith(".csv") ? "text/csv" : "text/plain";
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
