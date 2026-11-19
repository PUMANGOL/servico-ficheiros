package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.dashboard.AuditEventService;
import ao.co.pumangol.fileservice.security.AuthAttributes;
import ao.co.pumangol.fileservice.security.PublicUrls;
import ao.co.pumangol.fileservice.security.SignedUrlService;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints sem chave de API, pensados para o browser:
 * <ul>
 *   <li>leitura de ficheiros PUBLIC, ou de ficheiros privados através de um link assinado;</li>
 *   <li>upload através de um link assinado
 *       (validado em {@link ao.co.pumangol.fileservice.security.SignedUploadInterceptor}).</li>
 * </ul>
 */
@RestController
public class PublicFileController {

    /** Os ficheiros são imutáveis (um novo upload gera sempre um novo id), por isso podem ser guardados em cache. */
    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofHours(1)).cachePublic();
    private static final CacheControl SIGNED_CACHE = CacheControl.noCache().cachePrivate();

    private final FileService fileService;
    private final PublicUrls publicUrls;
    private final AuditEventService auditEvents;

    public PublicFileController(FileService fileService, PublicUrls publicUrls, AuditEventService auditEvents) {
        this.fileService = fileService;
        this.publicUrls = publicUrls;
        this.auditEvents = auditEvents;
    }

    @GetMapping("/api/v1/public/files/{id}")
    public ResponseEntity<Resource> content(
            @PathVariable("id") UUID id,
            @RequestParam(name = SignedUrlService.PARAM_EXPIRES, required = false) Long exp,
            @RequestParam(name = SignedUrlService.PARAM_SIGNATURE, required = false) String signature,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        FileContent content = fileService.openPublic(id, exp, signature);
        auditEvents.record("FILE_DOWNLOADED", null, id.toString(), "Conteúdo público ou assinado consultado");
        boolean isPublic = content.file().visibility() == Visibility.PUBLIC;
        return FileDownloads.respond(content, download, isPublic ? PUBLIC_CACHE : SIGNED_CACHE);
    }

    /**
     * Upload com link assinado (multipart/form-data, campo {@code file}). A pasta, a visibilidade
     * e o autor vêm da assinatura, não do formulário.
     */
    @PostMapping(path = "/api/v1/public/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(
            @RequestAttribute(AuthAttributes.UPLOAD_GRANT) UploadGrant grant,
            @RequestParam("file") MultipartFile file) {
        UploadCommand command = new UploadCommand(file.getOriginalFilename(), file.getSize(), file::getInputStream,
                grant.folder(), grant.visibility(), grant.uploadedBy());
        StoredFile stored = fileService.upload(grant.app(), command);
        auditEvents.record("FILE_UPLOADED", grant.app().code(), stored.id().toString(),
                "Upload assinado concluído (" + stored.visibility() + ", " + stored.sizeBytes() + " bytes)");
        FileResponse body = FileResponse.from(stored, publicUrls.publicFileUrl(stored.id()));
        return ResponseEntity.created(URI.create(publicUrls.fileMetadataUrl(stored.id()))).body(body);
    }
}
