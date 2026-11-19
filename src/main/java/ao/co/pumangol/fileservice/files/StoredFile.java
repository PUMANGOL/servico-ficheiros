package ao.co.pumangol.fileservice.files;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadados de um ficheiro guardado.
 *
 * @param storagePath  caminho relativo à raiz de armazenamento (nunca exposto na API)
 * @param contentType  tipo detectado pelo conteúdo, não o declarado pelo cliente
 * @param folder       agrupamento lógico definido pela aplicação (ex.: {@code facturas/2026}); opcional
 * @param uploadedBy   referência livre ao autor do upload na aplicação cliente; opcional
 * @param deletedAt    preenchido quando o ficheiro é eliminado (o registo fica para auditoria)
 */
public record StoredFile(
        UUID id,
        UUID applicationId,
        String originalFilename,
        String storagePath,
        String contentType,
        long sizeBytes,
        String checksumSha256,
        Visibility visibility,
        String folder,
        String uploadedBy,
        Instant createdAt,
        Instant deletedAt) {
}
