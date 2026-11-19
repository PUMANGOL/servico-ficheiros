package ao.co.pumangol.fileservice.support;

import ao.co.pumangol.fileservice.files.FileRepository;
import ao.co.pumangol.fileservice.files.StoredFile;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/** Repositório em memória para testar as regras de negócio sem base de dados. */
public class InMemoryFileRepository implements FileRepository {

    private final Map<UUID, StoredFile> files = new LinkedHashMap<>();
    private boolean failOnInsert;

    /** Simula uma falha da base de dados ao gravar os metadados. */
    public void failOnNextInsert() {
        this.failOnInsert = true;
    }

    @Override
    public void insert(StoredFile file) {
        if (failOnInsert) {
            failOnInsert = false;
            throw new IllegalStateException("Falha simulada da base de dados");
        }
        files.put(file.id(), file);
    }

    @Override
    public Optional<StoredFile> findActiveById(UUID id) {
        return Optional.ofNullable(files.get(id)).filter(file -> file.deletedAt() == null);
    }

    @Override
    public List<StoredFile> findActiveByApp(UUID applicationId, String folder, int limit, long offset) {
        return active(applicationId, folder)
                .sorted(Comparator.comparing(StoredFile::createdAt).reversed().thenComparing(StoredFile::id))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    @Override
    public long countActiveByApp(UUID applicationId, String folder) {
        return active(applicationId, folder).count();
    }

    @Override
    public long sumActiveSizeByApp(UUID applicationId) {
        return active(applicationId, null).mapToLong(StoredFile::sizeBytes).sum();
    }

    @Override
    public boolean markDeleted(UUID id, Instant deletedAt) {
        StoredFile file = files.get(id);
        if (file == null || file.deletedAt() != null) {
            return false;
        }
        files.put(id, new StoredFile(file.id(), file.applicationId(), file.originalFilename(), file.storagePath(),
                file.contentType(), file.sizeBytes(), file.checksumSha256(), file.visibility(), file.folder(),
                file.uploadedBy(), file.createdAt(), deletedAt));
        return true;
    }

    private Stream<StoredFile> active(UUID applicationId, String folder) {
        return files.values().stream()
                .filter(file -> file.deletedAt() == null)
                .filter(file -> file.applicationId().equals(applicationId))
                .filter(file -> folder == null || Objects.equals(folder, file.folder()));
    }
}
