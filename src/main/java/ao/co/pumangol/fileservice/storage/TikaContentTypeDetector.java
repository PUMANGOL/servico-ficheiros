package ao.co.pumangol.fileservice.storage;

import ao.co.pumangol.fileservice.common.ContentTypeRules;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tika.Tika;

/**
 * Detecção pelo conteúdo (magic bytes) com o Apache Tika. O nome do ficheiro só refina o
 * resultado quando é compatível com o conteúdo: um HTML chamado {@code foto.png} continua
 * a ser detectado como {@code text/html}.
 */
public class TikaContentTypeDetector implements ContentTypeDetector {

    private final Tika tika = new Tika();

    @Override
    public String detect(Path file, String filename) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            return ContentTypeRules.normalize(tika.detect(in, filename));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao analisar o tipo do ficheiro", e);
        }
    }
}
