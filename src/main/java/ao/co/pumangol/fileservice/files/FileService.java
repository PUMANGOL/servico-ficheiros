package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.apps.AppRepository;
import ao.co.pumangol.fileservice.apps.RegisteredApp;
import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.common.ContentTypeRules;
import ao.co.pumangol.fileservice.common.Filenames;
import ao.co.pumangol.fileservice.common.PageResult;
import ao.co.pumangol.fileservice.security.SignedLink;
import ao.co.pumangol.fileservice.security.SignedUrlService;
import ao.co.pumangol.fileservice.storage.ContentTypeDetector;
import ao.co.pumangol.fileservice.storage.FileStorage;
import ao.co.pumangol.fileservice.storage.FileTooLargeException;
import ao.co.pumangol.fileservice.storage.TempUpload;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Regras de negócio dos ficheiros: upload, consulta, download, eliminação e links temporários.
 * <p>
 * Todas as operações autenticadas recebem a aplicação que fez o pedido e só vêem os
 * ficheiros dessa aplicação: um identificador de outra aplicação responde 404.
 */
public class FileService {

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /** Segmentos alfanuméricos (com _ e -) separados por barras: {@code facturas/2026}. */
    private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*(/[A-Za-z0-9][A-Za-z0-9_-]*)*");
    private static final int MAX_FOLDER_LENGTH = 100;
    private static final int MAX_UPLOADED_BY_LENGTH = 150;

    private final FileRepository files;
    private final AppRepository apps;
    private final FileStorage storage;
    private final ContentTypeDetector detector;
    private final SignedUrlService signedUrls;
    private final UploadLimits limits;
    private final Clock clock;

    public FileService(FileRepository files, AppRepository apps, FileStorage storage, ContentTypeDetector detector,
                       SignedUrlService signedUrls, UploadLimits limits, Clock clock) {
        this.files = files;
        this.apps = apps;
        this.storage = storage;
        this.detector = detector;
        this.signedUrls = signedUrls;
        this.limits = limits;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ upload

    public StoredFile upload(RegisteredApp app, UploadCommand command) {
        String folder = normalizeFolder(command.folder());
        String uploadedBy = normalizeUploadedBy(command.uploadedBy());
        Visibility visibility = command.visibility() == null ? Visibility.PRIVATE : command.visibility();
        String filename = Filenames.sanitize(command.originalFilename());

        long maxSize = app.maxFileSizeBytes() != null ? app.maxFileSizeBytes() : limits.maxFileSizeBytes();
        if (command.declaredSize() > maxSize) {
            throw fileTooLarge(maxSize);
        }
        long usedBytes = 0;
        if (app.storageQuotaBytes() != null) {
            usedBytes = files.sumActiveSizeByApp(app.id());
            ensureWithinQuota(app, usedBytes, Math.max(command.declaredSize(), 0));
        }

        TempUpload temp = receive(command, maxSize);
        String storagePath = null;
        boolean saved = false;
        try {
            if (temp.size() == 0) {
                throw ApiException.badRequest("EMPTY_FILE", "O ficheiro enviado está vazio.");
            }
            if (app.storageQuotaBytes() != null) {
                ensureWithinQuota(app, usedBytes, temp.size());
            }

            String contentType = detector.detect(temp.path(), filename);
            List<String> allowed = app.allowedContentTypes().isEmpty()
                    ? limits.allowedContentTypes()
                    : app.allowedContentTypes();
            if (!ContentTypeRules.isAllowed(contentType, allowed)) {
                throw ApiException.unsupportedMediaType(
                        "Tipo de ficheiro não permitido para esta aplicação: " + contentType + ".");
            }

            UUID id = UUID.randomUUID();
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            storagePath = storage.commit(temp, app.code(), id, now);
            StoredFile file = new StoredFile(id, app.id(), filename, storagePath, contentType, temp.size(),
                    temp.sha256(), visibility, folder, uploadedBy, now, null);
            files.insert(file);
            saved = true;

            log.info("Upload: app={} id={} tipo={} bytes={} visibilidade={}",
                    app.code(), id, contentType, temp.size(), visibility);
            return file;
        } finally {
            if (!saved) {
                // Nada fica no disco se a validação ou a gravação dos metadados falhar.
                storage.discard(temp);
                if (storagePath != null) {
                    storage.delete(storagePath);
                }
            }
        }
    }

    private TempUpload receive(UploadCommand command, long maxSize) {
        try (InputStream in = command.content().open()) {
            return storage.receive(in, maxSize);
        } catch (FileTooLargeException e) {
            throw fileTooLarge(maxSize);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o ficheiro enviado", e);
        }
    }

    // ---------------------------------------------------------------- consulta

    public StoredFile get(RegisteredApp app, UUID id) {
        return files.findActiveById(id)
                .filter(file -> file.applicationId().equals(app.id()))
                .orElseThrow(FileService::fileNotFound);
    }

    public PageResult<StoredFile> list(RegisteredApp app, String folder, Integer page, Integer size) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        if (pageNumber < 0 || pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("INVALID_PAGINATION",
                    "Paginação inválida: page >= 0 e size entre 1 e " + MAX_PAGE_SIZE + ".");
        }
        String folderFilter = normalizeFolder(folder);
        long total = files.countActiveByApp(app.id(), folderFilter);
        List<StoredFile> items = files.findActiveByApp(app.id(), folderFilter, pageSize, (long) pageNumber * pageSize);
        return new PageResult<>(items, pageNumber, pageSize, total);
    }

    // ---------------------------------------------------------------- download

    public FileContent open(RegisteredApp app, UUID id) {
        return content(get(app, id));
    }

    public FileContent openForAdmin(UUID id) {
        StoredFile file = files.findActiveById(id).orElseThrow(FileService::fileNotFound);
        return content(file);
    }

    /**
     * Acesso sem credenciais: ficheiros PUBLIC, ou ficheiros PRIVATE com link assinado válido.
     * Sem assinatura, um ficheiro privado responde 404 para não revelar que existe.
     */
    public FileContent openPublic(UUID id, Long exp, String signature) {
        StoredFile file = files.findActiveById(id).orElseThrow(FileService::fileNotFound);
        if (file.visibility() != Visibility.PUBLIC) {
            if (exp == null || signature == null) {
                throw fileNotFound();
            }
            if (!signedUrls.isValidDownload(id, exp, signature)) {
                throw invalidLink();
            }
        }
        boolean ownerActive = apps.findById(file.applicationId()).map(RegisteredApp::active).orElse(false);
        if (!ownerActive) {
            throw fileNotFound();
        }
        return content(file);
    }

    private FileContent content(StoredFile file) {
        Path path = storage.locate(file.storagePath());
        if (!Files.isRegularFile(path)) {
            log.error("Ficheiro em falta no armazenamento: id={} caminho={}", file.id(), file.storagePath());
            throw new ApiException(500, "FILE_MISSING", "O conteúdo do ficheiro não está disponível no armazenamento.");
        }
        return new FileContent(file, path);
    }

    // -------------------------------------------------------------- eliminação

    /** Marca o registo como eliminado (fica para auditoria) e remove o conteúdo do disco. */
    public void delete(RegisteredApp app, UUID id) {
        StoredFile file = get(app, id);
        if (files.markDeleted(file.id(), clock.instant().truncatedTo(ChronoUnit.MILLIS))) {
            storage.delete(file.storagePath());
            log.info("Eliminado: app={} id={}", app.code(), id);
        }
    }

    // ------------------------------------------------------- links temporários

    public SignedLink createDownloadLink(RegisteredApp app, UUID id, Long ttlSeconds, String baseUrl) {
        StoredFile file = get(app, id);
        return signedUrls.createDownloadLink(baseUrl, file.id(), ttlSeconds);
    }

    public SignedLink createUploadLink(RegisteredApp app, String folder, String visibility, String uploadedBy,
                                       Long ttlSeconds, String baseUrl) {
        return signedUrls.createUploadLink(baseUrl, app.code(), normalizeFolder(folder), Visibility.parse(visibility),
                normalizeUploadedBy(uploadedBy), ttlSeconds);
    }

    /**
     * Valida os parâmetros de um link de upload assinado (lidos da query string) e devolve
     * a autorização correspondente. A assinatura é verificada antes de qualquer acesso à base de dados.
     */
    public UploadGrant authorizeSignedUpload(Map<String, String> params) {
        String appCode = emptyToNull(params.get(SignedUrlService.PARAM_APP));
        String signature = emptyToNull(params.get(SignedUrlService.PARAM_SIGNATURE));
        String folder = emptyToNull(params.get(SignedUrlService.PARAM_FOLDER));
        String uploadedBy = emptyToNull(params.get(SignedUrlService.PARAM_UPLOADED_BY));
        Long exp = parseLongOrNull(params.get(SignedUrlService.PARAM_EXPIRES));
        Visibility visibility = parseVisibilityOrNull(params.get(SignedUrlService.PARAM_VISIBILITY));

        if (appCode == null || signature == null || exp == null || visibility == null
                || !signedUrls.isValidUpload(appCode, folder, visibility, uploadedBy, exp, signature)) {
            throw invalidLink();
        }
        RegisteredApp app = apps.findByCode(appCode)
                .filter(RegisteredApp::active)
                .orElseThrow(FileService::invalidLink);
        return new UploadGrant(app, folder, visibility, uploadedBy);
    }

    // ---------------------------------------------------------------- validação

    static String normalizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return null;
        }
        String value = folder.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            return null;
        }
        if (value.length() > MAX_FOLDER_LENGTH || !FOLDER.matcher(value).matches()) {
            throw ApiException.badRequest("INVALID_FOLDER",
                    "Pasta inválida: use letras, algarismos, '_' e '-', com '/' entre níveis (máx. "
                            + MAX_FOLDER_LENGTH + " caracteres).");
        }
        return value;
    }

    static String normalizeUploadedBy(String uploadedBy) {
        if (uploadedBy == null || uploadedBy.isBlank()) {
            return null;
        }
        String value = uploadedBy.trim();
        boolean hasControlChars = value.chars().anyMatch(Character::isISOControl);
        if (value.length() > MAX_UPLOADED_BY_LENGTH || hasControlChars) {
            throw ApiException.badRequest("INVALID_UPLOADED_BY",
                    "O campo 'uploadedBy' tem no máximo " + MAX_UPLOADED_BY_LENGTH + " caracteres.");
        }
        return value;
    }

    private static void ensureWithinQuota(RegisteredApp app, long usedBytes, long incomingBytes) {
        if (usedBytes + incomingBytes > app.storageQuotaBytes()) {
            throw ApiException.payloadTooLarge("QUOTA_EXCEEDED",
                    "A quota de armazenamento da aplicação (" + app.storageQuotaBytes() + " bytes) seria ultrapassada.");
        }
    }

    private static ApiException fileTooLarge(long maxSize) {
        return ApiException.payloadTooLarge("FILE_TOO_LARGE",
                "O ficheiro ultrapassa o tamanho máximo permitido (" + maxSize + " bytes).");
    }

    private static ApiException fileNotFound() {
        return ApiException.notFound("Ficheiro não encontrado.");
    }

    private static ApiException invalidLink() {
        return ApiException.forbidden("INVALID_SIGNATURE", "Link inválido ou expirado.");
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static Long parseLongOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Visibility parseVisibilityOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Visibility.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
