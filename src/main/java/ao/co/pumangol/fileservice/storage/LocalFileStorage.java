package ao.co.pumangol.fileservice.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Armazenamento no disco local do servidor.
 * <pre>
 *   {raiz}/.tmp/{uuid}.part                 uploads em curso
 *   {raiz}/{aplicação}/{ano}/{mês}/{uuid}   ficheiros confirmados
 * </pre>
 * O nome no disco é sempre o UUID do ficheiro: nada do que o cliente envia entra no caminho.
 * A área temporária fica na mesma raiz para que a confirmação seja um {@code rename} atómico.
 */
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final String TEMP_DIR = ".tmp";
    private static final String TEMP_SUFFIX = ".part";
    private static final Duration STALE_TEMP_AGE = Duration.ofHours(24);
    private static final int BUFFER_SIZE = 64 * 1024;

    private final Path root;
    private final Path tempDir;

    public LocalFileStorage(Path basePath) {
        try {
            Files.createDirectories(basePath);
            this.root = basePath.toRealPath();
            this.tempDir = Files.createDirectories(root.resolve(TEMP_DIR));
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível preparar a pasta de armazenamento: " + basePath, e);
        }
        if (!Files.isWritable(root) || !Files.isWritable(tempDir)) {
            throw new IllegalStateException("Sem permissão de escrita na pasta de armazenamento: " + root);
        }
        removeStaleTempFiles();
        log.info("Armazenamento de ficheiros em {}", root);
    }

    @Override
    public TempUpload receive(InputStream content, long maxBytes) {
        Path temp = tempDir.resolve(UUID.randomUUID() + TEMP_SUFFIX);
        MessageDigest digest = newSha256();
        long total = 0;
        try (OutputStream out = Files.newOutputStream(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = content.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new FileTooLargeException(maxBytes);
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Falha ao gravar o ficheiro recebido", e);
        } catch (RuntimeException e) {
            deleteQuietly(temp);
            throw e;
        }
        return new TempUpload(temp, total, HexFormat.of().formatHex(digest.digest()));
    }

    @Override
    public String commit(TempUpload upload, String appCode, UUID fileId, Instant when) {
        ZonedDateTime utc = when.atZone(ZoneOffset.UTC);
        String storagePath = String.format("%s/%04d/%02d/%s", appCode, utc.getYear(), utc.getMonthValue(), fileId);
        Path target = locate(storagePath);
        try {
            Files.createDirectories(target.getParent());
            try {
                Files.move(upload.path(), target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(upload.path(), target);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao mover o ficheiro para o destino final: " + storagePath, e);
        }
        return storagePath;
    }

    @Override
    public void discard(TempUpload upload) {
        deleteQuietly(upload.path());
    }

    @Override
    public Path locate(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new IllegalArgumentException("Caminho de armazenamento vazio");
        }
        Path resolved = root.resolve(storagePath).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root) || resolved.startsWith(tempDir)) {
            throw new IllegalArgumentException("Caminho fora da pasta de armazenamento: " + storagePath);
        }
        return resolved;
    }

    @Override
    public boolean delete(String storagePath) {
        try {
            return Files.deleteIfExists(locate(storagePath));
        } catch (IOException e) {
            log.warn("Não foi possível eliminar o ficheiro {}: {}", storagePath, e.toString());
            return false;
        }
    }

    /** Restos de uploads interrompidos (por exemplo, por um reinício do serviço). */
    private void removeStaleTempFiles() {
        Instant limit = Instant.now().minus(STALE_TEMP_AGE);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir, "*" + TEMP_SUFFIX)) {
            for (Path file : stream) {
                if (Files.getLastModifiedTime(file).toInstant().isBefore(limit)) {
                    deleteQuietly(file);
                }
            }
        } catch (IOException e) {
            log.warn("Não foi possível limpar a área temporária {}: {}", tempDir, e.toString());
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Não foi possível eliminar o ficheiro temporário {}: {}", path, e.toString());
        }
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
