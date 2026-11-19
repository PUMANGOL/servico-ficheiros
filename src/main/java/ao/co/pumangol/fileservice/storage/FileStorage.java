package ao.co.pumangol.fileservice.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/**
 * Armazenamento físico dos ficheiros. A implementação actual grava no disco local
 * ({@link LocalFileStorage}); esta interface é o ponto de extensão para outro destino
 * (NFS, S3/MinIO, Azure Blob) sem tocar no resto do serviço.
 */
public interface FileStorage {

    /**
     * Recebe o conteúdo para uma área temporária, calculando tamanho e SHA-256.
     *
     * @throws FileTooLargeException se o conteúdo ultrapassar {@code maxBytes}
     */
    TempUpload receive(InputStream content, long maxBytes);

    /** Move o ficheiro temporário para o destino final e devolve o caminho relativo a guardar. */
    String commit(TempUpload upload, String appCode, UUID fileId, Instant when);

    /** Elimina um ficheiro temporário que não chegou a ser confirmado. */
    void discard(TempUpload upload);

    /** Resolve um caminho relativo guardado para o caminho real, garantindo que fica dentro da raiz. */
    Path locate(String storagePath);

    /** Elimina o ficheiro; devolve {@code false} se já não existia ou não foi possível eliminar. */
    boolean delete(String storagePath);
}
