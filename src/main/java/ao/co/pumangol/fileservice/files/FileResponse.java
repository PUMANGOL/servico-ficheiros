package ao.co.pumangol.fileservice.files;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadados de um ficheiro devolvidos pela API.
 *
 * @param publicUrl URL estável de acesso sem credenciais; só preenchido em ficheiros PUBLIC
 */
public record FileResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Visibility visibility,
        String folder,
        String uploadedBy,
        Instant createdAt,
        String publicUrl) {

    public static FileResponse from(StoredFile file, String publicUrl) {
        return new FileResponse(
                file.id(),
                file.originalFilename(),
                file.contentType(),
                file.sizeBytes(),
                file.checksumSha256(),
                file.visibility(),
                file.folder(),
                file.uploadedBy(),
                file.createdAt(),
                file.visibility() == Visibility.PUBLIC ? publicUrl : null);
    }
}
