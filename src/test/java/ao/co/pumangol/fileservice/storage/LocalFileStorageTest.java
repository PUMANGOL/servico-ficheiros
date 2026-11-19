package ao.co.pumangol.fileservice.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path baseDir;

    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileStorage(baseDir.resolve("storage"));
    }

    @Test
    void receivesContentComputingSizeAndChecksum() throws IOException {
        TempUpload upload = storage.receive(stream("abc"), 1024);

        assertEquals(3, upload.size());
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", upload.sha256());
        assertEquals("abc", Files.readString(upload.path()));
    }

    @Test
    void commitMovesTheFileUnderTheApplicationFolder() throws IOException {
        UUID fileId = UUID.randomUUID();
        TempUpload upload = storage.receive(stream("conteúdo"), 1024);

        String storagePath = storage.commit(upload, "portal", fileId, Instant.parse("2026-03-09T12:00:00Z"));

        assertEquals("portal/2026/03/" + fileId, storagePath);
        assertFalse(Files.exists(upload.path()));
        assertArrayEquals("conteúdo".getBytes(StandardCharsets.UTF_8), Files.readAllBytes(storage.locate(storagePath)));
        assertEquals(0, tempFileCount());
    }

    @Test
    void rejectsContentAboveTheLimitAndLeavesNothingBehind() throws IOException {
        assertThrows(FileTooLargeException.class, () -> storage.receive(stream("0123456789"), 5));
        assertEquals(0, tempFileCount());
    }

    @Test
    void acceptsContentExactlyAtTheLimit() {
        assertEquals(10, storage.receive(stream("0123456789"), 10).size());
    }

    @Test
    void discardRemovesTheTemporaryFile() throws IOException {
        TempUpload upload = storage.receive(stream("abc"), 1024);
        storage.discard(upload);
        assertEquals(0, tempFileCount());
    }

    @Test
    void deleteRemovesTheFileAndIsIdempotent() {
        TempUpload upload = storage.receive(stream("abc"), 1024);
        String storagePath = storage.commit(upload, "portal", UUID.randomUUID(), Instant.now());

        assertTrue(storage.delete(storagePath));
        assertFalse(storage.delete(storagePath));
    }

    @Test
    void neverResolvesPathsOutsideTheStorageRoot() {
        assertThrows(IllegalArgumentException.class, () -> storage.locate("../fora.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.locate("portal/../../fora.txt"));
        assertThrows(IllegalArgumentException.class, () -> storage.locate("/etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> storage.locate(".tmp/qualquer.part"));
        assertThrows(IllegalArgumentException.class, () -> storage.locate(""));
        assertThrows(IllegalArgumentException.class, () -> storage.locate("."));
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private long tempFileCount() throws IOException {
        try (Stream<Path> files = Files.list(baseDir.resolve("storage").resolve(".tmp"))) {
            return files.count();
        }
    }
}
