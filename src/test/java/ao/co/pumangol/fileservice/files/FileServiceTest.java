package ao.co.pumangol.fileservice.files;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ao.co.pumangol.fileservice.apps.AppDefinition;
import ao.co.pumangol.fileservice.apps.AppService;
import ao.co.pumangol.fileservice.apps.RegisteredApp;
import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.common.PageResult;
import ao.co.pumangol.fileservice.common.QueryString;
import ao.co.pumangol.fileservice.security.SignedLink;
import ao.co.pumangol.fileservice.security.SignedUrlService;
import ao.co.pumangol.fileservice.security.UrlSigner;
import ao.co.pumangol.fileservice.storage.LocalFileStorage;
import ao.co.pumangol.fileservice.support.InMemoryAppRepository;
import ao.co.pumangol.fileservice.support.InMemoryFileRepository;
import ao.co.pumangol.fileservice.support.MutableClock;
import ao.co.pumangol.fileservice.support.SimpleContentTypeDetector;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileServiceTest {

    private static final String BASE_URL = "https://ficheiros.exemplo.co.ao";
    private static final byte[] PNG = png(200);

    @TempDir
    Path baseDir;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
    private final InMemoryAppRepository appRepository = new InMemoryAppRepository();
    private final InMemoryFileRepository fileRepository = new InMemoryFileRepository();
    private final AppService appService = new AppService(appRepository, clock);

    private FileService service;
    private RegisteredApp portal;
    private RegisteredApp loja;

    @BeforeEach
    void setUp() {
        SignedUrlService signedUrls = new SignedUrlService(
                new UrlSigner("segredo-de-teste-com-mais-de-32-caracteres!"),
                Duration.ofMinutes(5), Duration.ofHours(24), clock);
        UploadLimits limits = new UploadLimits(1024, List.of("image/png", "application/pdf", "text/plain", "text/csv"));
        service = new FileService(fileRepository, appRepository, new LocalFileStorage(baseDir.resolve("storage")),
                new SimpleContentTypeDetector(), signedUrls, limits, clock);

        portal = appService.create(new AppDefinition("portal", "Portal", null, null, null, null, null)).app();
        loja = appService.create(new AppDefinition("loja", "Loja", null, null, null, null, null)).app();
    }

    // ------------------------------------------------------------------ upload

    @Test
    void uploadStoresContentAndMetadata() throws IOException {
        StoredFile file = service.upload(portal, upload("C:\\fotos\\Logótipo.png", PNG, "imagens/logos", Visibility.PUBLIC, "u-42"));

        assertEquals("Logótipo.png", file.originalFilename());
        assertEquals("image/png", file.contentType());
        assertEquals(PNG.length, file.sizeBytes());
        assertEquals(64, file.checksumSha256().length());
        assertEquals(Visibility.PUBLIC, file.visibility());
        assertEquals("imagens/logos", file.folder());
        assertEquals("u-42", file.uploadedBy());
        assertEquals(portal.id(), file.applicationId());
        assertEquals("portal/2026/11/" + file.id(), file.storagePath());

        FileContent content = service.open(portal, file.id());
        assertArrayEquals(PNG, Files.readAllBytes(content.path()));
        assertEquals(file, service.get(portal, file.id()));
    }

    @Test
    void uploadDefaultsToPrivateWithoutFolder() {
        StoredFile file = service.upload(portal, upload("nota.txt", "olá".getBytes(StandardCharsets.UTF_8), " ", null, null));

        assertEquals(Visibility.PRIVATE, file.visibility());
        assertNull(file.folder());
        assertNull(file.uploadedBy());
        assertEquals("text/plain", file.contentType());
    }

    @Test
    void contentTypeComesFromTheContentNotFromTheName() {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);

        ApiException error = assertStatus(415, () -> service.upload(portal, upload("foto.png", html, null, null, null)));

        assertEquals("CONTENT_TYPE_NOT_ALLOWED", error.getCode());
        assertNothingStored();
    }

    @Test
    void rejectsEmptyFiles() {
        ApiException error = assertStatus(400, () -> service.upload(portal, upload("vazio.txt", new byte[0], null, null, null)));
        assertEquals("EMPTY_FILE", error.getCode());
        assertNothingStored();
    }

    @Test
    void rejectsFilesAboveTheDefaultLimitEvenWhenTheDeclaredSizeLies() {
        byte[] big = png(2000);

        assertEquals("FILE_TOO_LARGE", assertStatus(413, () -> service.upload(portal, upload("a.png", big, null, null, null))).getCode());

        UploadCommand lying = new UploadCommand("a.png", 10, () -> new ByteArrayInputStream(big), null, null, null);
        assertEquals("FILE_TOO_LARGE", assertStatus(413, () -> service.upload(portal, lying)).getCode());
        assertNothingStored();
    }

    @Test
    void applicationLimitsOverrideTheDefaults() {
        RegisteredApp restricted = appService.create(new AppDefinition("restrita", "Restrita", null,
                100L, List.of("application/pdf"), null, null)).app();
        byte[] pdf = "%PDF-1.7 conteúdo".getBytes(StandardCharsets.UTF_8);

        assertEquals("application/pdf", service.upload(restricted, upload("doc.pdf", pdf, null, null, null)).contentType());
        assertStatus(415, () -> service.upload(restricted, upload("foto.png", png(50), null, null, null)));
        assertStatus(413, () -> service.upload(restricted, upload("grande.pdf", pad(pdf, 150), null, null, null)));
    }

    @Test
    void enforcesTheStorageQuota() {
        RegisteredApp limited = appService.create(new AppDefinition("limitada", "Limitada", null,
                null, null, 500L, null)).app();

        StoredFile first = service.upload(limited, upload("a.png", png(300), null, null, null));
        ApiException error = assertStatus(413, () -> service.upload(limited, upload("b.png", png(300), null, null, null)));
        assertEquals("QUOTA_EXCEEDED", error.getCode());

        // Eliminar liberta espaço.
        service.delete(limited, first.id());
        service.upload(limited, upload("b.png", png(300), null, null, null));
    }

    @Test
    void rejectsInvalidFolderAndUploadedBy() {
        assertEquals("INVALID_FOLDER", assertStatus(400, () -> service.upload(portal, upload("a.png", PNG, "../segredos", null, null))).getCode());
        assertEquals("INVALID_FOLDER", assertStatus(400, () -> service.upload(portal, upload("a.png", PNG, "com espaço", null, null))).getCode());
        assertEquals("INVALID_UPLOADED_BY", assertStatus(400, () -> service.upload(portal, upload("a.png", PNG, null, null, "x".repeat(151)))).getCode());
        assertNothingStored();
    }

    @Test
    void nothingStaysOnDiskWhenSavingMetadataFails() {
        fileRepository.failOnNextInsert();

        assertThrows(IllegalStateException.class, () -> service.upload(portal, upload("a.png", PNG, null, null, null)));

        assertNothingStored();
    }

    // -------------------------------------------------------------- isolamento

    @Test
    void applicationsOnlySeeTheirOwnFiles() {
        StoredFile file = service.upload(portal, upload("a.png", PNG, null, null, null));

        assertStatus(404, () -> service.get(loja, file.id()));
        assertStatus(404, () -> service.open(loja, file.id()));
        assertStatus(404, () -> service.delete(loja, file.id()));
        assertStatus(404, () -> service.createDownloadLink(loja, file.id(), null, BASE_URL));
        assertEquals(0, service.list(loja, null, null, null).totalItems());
        assertEquals(file, service.get(portal, file.id()));
    }

    // ---------------------------------------------------------------- listagem

    @Test
    void listsNewestFirstWithPaginationAndFolderFilter() {
        StoredFile first = service.upload(portal, upload("1.png", PNG, "a", null, null));
        clock.advance(Duration.ofSeconds(1));
        StoredFile second = service.upload(portal, upload("2.png", PNG, "b", null, null));
        clock.advance(Duration.ofSeconds(1));
        StoredFile third = service.upload(portal, upload("3.png", PNG, "a", null, null));

        PageResult<StoredFile> all = service.list(portal, null, null, null);
        assertEquals(List.of(third.id(), second.id(), first.id()), ids(all));
        assertEquals(3, all.totalItems());
        assertEquals(FileService.DEFAULT_PAGE_SIZE, all.size());

        PageResult<StoredFile> secondPage = service.list(portal, null, 1, 2);
        assertEquals(List.of(first.id()), ids(secondPage));
        assertEquals(2, secondPage.totalPages());

        assertEquals(List.of(third.id(), first.id()), ids(service.list(portal, "/a/", null, null)));

        assertStatus(400, () -> service.list(portal, null, -1, null));
        assertStatus(400, () -> service.list(portal, null, 0, 0));
        assertStatus(400, () -> service.list(portal, null, 0, FileService.MAX_PAGE_SIZE + 1));
    }

    // -------------------------------------------------------------- eliminação

    @Test
    void deleteRemovesContentAndHidesTheFile() {
        StoredFile file = service.upload(portal, upload("a.png", PNG, null, null, null));
        Path path = service.open(portal, file.id()).path();

        service.delete(portal, file.id());

        assertFalse(Files.exists(path));
        assertStatus(404, () -> service.get(portal, file.id()));
        assertStatus(404, () -> service.delete(portal, file.id()));
        assertEquals(0, service.list(portal, null, null, null).totalItems());
    }

    // ------------------------------------------------------ acesso sem credenciais

    @Test
    void publicFilesAreReadableWithoutSignature() {
        StoredFile file = service.upload(portal, upload("a.png", PNG, null, Visibility.PUBLIC, null));
        assertEquals(file, service.openPublic(file.id(), null, null).file());
    }

    @Test
    void privateFilesNeedAValidSignedLink() {
        StoredFile file = service.upload(portal, upload("a.png", PNG, null, null, null));
        Map<String, String> params = queryOf(service.createDownloadLink(portal, file.id(), 60L, BASE_URL));
        long exp = Long.parseLong(params.get("exp"));
        String sig = params.get("sig");

        assertEquals(file, service.openPublic(file.id(), exp, sig).file());

        // Sem assinatura responde 404: não revela que o ficheiro existe.
        assertStatus(404, () -> service.openPublic(file.id(), null, null));
        assertEquals("INVALID_SIGNATURE", assertStatus(403, () -> service.openPublic(file.id(), exp, sig + "x")).getCode());
        assertStatus(403, () -> service.openPublic(file.id(), exp + 60, sig));

        clock.advance(Duration.ofSeconds(61));
        assertStatus(403, () -> service.openPublic(file.id(), exp, sig));
    }

    @Test
    void filesOfDisabledApplicationsAreNotServedPublicly() {
        StoredFile file = service.upload(portal, upload("a.png", PNG, null, Visibility.PUBLIC, null));
        appService.update("portal", new AppDefinition(null, "Portal", null, null, null, null, false));

        assertStatus(404, () -> service.openPublic(file.id(), null, null));
    }

    @Test
    void unknownFileIsNotFound() {
        assertStatus(404, () -> service.openPublic(UUID.randomUUID(), null, null));
        assertStatus(404, () -> service.get(portal, UUID.randomUUID()));
    }

    // ------------------------------------------------------ upload por link assinado

    @Test
    void signedUploadLinkAuthorizesUploadsWithFixedConditions() {
        SignedLink link = service.createUploadLink(portal, "/avatars/", "public", "u-7", 120L, BASE_URL);
        Map<String, String> params = queryOf(link);

        UploadGrant grant = service.authorizeSignedUpload(params);

        assertEquals("portal", grant.app().code());
        assertEquals("avatars", grant.folder());
        assertEquals(Visibility.PUBLIC, grant.visibility());
        assertEquals("u-7", grant.uploadedBy());

        StoredFile file = service.upload(grant.app(),
                new UploadCommand("eu.png", PNG.length, () -> new ByteArrayInputStream(PNG),
                        grant.folder(), grant.visibility(), grant.uploadedBy()));
        assertEquals("avatars", file.folder());
        assertEquals(Visibility.PUBLIC, file.visibility());
    }

    @Test
    void signedUploadLinkCannotBeTamperedWith() {
        Map<String, String> params = queryOf(service.createUploadLink(portal, "avatars", "PRIVATE", null, 120L, BASE_URL));

        assertInvalidLink(with(params, "folder", "outra"));
        assertInvalidLink(with(params, "visibility", "PUBLIC"));
        assertInvalidLink(with(params, "app", "loja"));
        assertInvalidLink(with(params, "by", "intruso"));
        assertInvalidLink(with(params, "exp", String.valueOf(Long.parseLong(params.get("exp")) + 3600)));
        assertInvalidLink(with(params, "exp", "amanha"));
        assertInvalidLink(with(params, "sig", null));
        assertInvalidLink(Map.of());

        clock.advance(Duration.ofSeconds(121));
        assertInvalidLink(params);
    }

    @Test
    void signedUploadLinkStopsWorkingWhenTheApplicationIsDisabled() {
        Map<String, String> params = queryOf(service.createUploadLink(portal, null, null, null, null, BASE_URL));
        appService.update("portal", new AppDefinition(null, "Portal", null, null, null, null, false));

        assertInvalidLink(params);
    }

    // ------------------------------------------------------------------ apoio

    private static UploadCommand upload(String filename, byte[] content, String folder, Visibility visibility, String uploadedBy) {
        return new UploadCommand(filename, content.length, () -> new ByteArrayInputStream(content), folder, visibility, uploadedBy);
    }

    private static byte[] png(int size) {
        return pad(SimpleContentTypeDetector.PNG_HEADER, size);
    }

    private static byte[] pad(byte[] head, int size) {
        byte[] result = new byte[size];
        System.arraycopy(head, 0, result, 0, Math.min(head.length, size));
        return result;
    }

    private static List<UUID> ids(PageResult<StoredFile> page) {
        return page.items().stream().map(StoredFile::id).toList();
    }

    private static Map<String, String> queryOf(SignedLink link) {
        return QueryString.parse(link.url().substring(link.url().indexOf('?') + 1));
    }

    private static Map<String, String> with(Map<String, String> params, String name, String value) {
        Map<String, String> copy = new java.util.HashMap<>(params);
        if (value == null) {
            copy.remove(name);
        } else {
            copy.put(name, value);
        }
        return copy;
    }

    private void assertInvalidLink(Map<String, String> params) {
        ApiException error = assertStatus(403, () -> service.authorizeSignedUpload(params));
        assertEquals("INVALID_SIGNATURE", error.getCode());
    }

    private void assertNothingStored() {
        try (Stream<Path> paths = Files.walk(baseDir.resolve("storage"))) {
            List<Path> regularFiles = paths.filter(Files::isRegularFile).toList();
            assertTrue(regularFiles.isEmpty(), "Ficheiros inesperados no disco: " + regularFiles);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        assertEquals(0, fileRepository.countActiveByApp(portal.id(), null));
    }

    private static ApiException assertStatus(int expectedStatus, Runnable action) {
        ApiException error = assertThrows(ApiException.class, action::run);
        assertEquals(expectedStatus, error.getStatus(), error.getMessage());
        return error;
    }
}
