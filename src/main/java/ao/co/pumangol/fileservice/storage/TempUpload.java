package ao.co.pumangol.fileservice.storage;

import java.nio.file.Path;

/** Ficheiro recebido para a área temporária, ainda por validar e confirmar. */
public record TempUpload(Path path, long size, String sha256) {
}
