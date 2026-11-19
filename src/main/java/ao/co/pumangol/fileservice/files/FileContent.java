package ao.co.pumangol.fileservice.files;

import java.nio.file.Path;

/** Um ficheiro pronto a ser servido: metadados e localização no disco. */
public record FileContent(StoredFile file, Path path) {
}
