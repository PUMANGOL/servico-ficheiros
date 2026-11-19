package ao.co.pumangol.fileservice.files;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Acesso aos metadados dos ficheiros. "Activo" significa não eliminado. */
public interface FileRepository {

    void insert(StoredFile file);

    Optional<StoredFile> findActiveById(UUID id);

    /** Mais recentes primeiro. {@code folder == null} não filtra por pasta. */
    List<StoredFile> findActiveByApp(UUID applicationId, String folder, int limit, long offset);

    long countActiveByApp(UUID applicationId, String folder);

    /** Espaço ocupado pelos ficheiros activos da aplicação, em bytes. */
    long sumActiveSizeByApp(UUID applicationId);

    /** Marca como eliminado; devolve {@code false} se já estava eliminado ou não existe. */
    boolean markDeleted(UUID id, Instant deletedAt);
}
