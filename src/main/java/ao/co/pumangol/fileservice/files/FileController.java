package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.apps.RegisteredApp;
import ao.co.pumangol.fileservice.dashboard.AuditEventService;
import ao.co.pumangol.fileservice.files.LinkRequests.DownloadLinkRequest;
import ao.co.pumangol.fileservice.files.LinkRequests.LinkResponse;
import ao.co.pumangol.fileservice.files.LinkRequests.UploadLinkRequest;
import ao.co.pumangol.fileservice.security.AuthAttributes;
import ao.co.pumangol.fileservice.security.PublicUrls;
import ao.co.pumangol.fileservice.security.SignedLink;
import java.net.URI;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * API das aplicações clientes. Todos os endpoints exigem {@code X-App-Id} + {@code X-Api-Key}
 * (ver {@link ao.co.pumangol.fileservice.security.ApiKeyInterceptor}) e operam apenas sobre
 * os ficheiros da aplicação autenticada.
 */
@RestController
public class FileController {

    private final FileService fileService;
    private final PublicUrls publicUrls;
    private final AuditEventService auditEvents;

    public FileController(FileService fileService, PublicUrls publicUrls, AuditEventService auditEvents) {
        this.fileService = fileService;
        this.publicUrls = publicUrls;
        this.auditEvents = auditEvents;
    }

    /** Upload de um ficheiro (multipart/form-data, campo {@code file}). */
    @PostMapping(path = "/api/v1/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileResponse> upload(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "folder", required = false) String folder,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "uploadedBy", required = false) String uploadedBy) {
        UploadCommand command = new UploadCommand(file.getOriginalFilename(), file.getSize(), file::getInputStream,
                folder, Visibility.parse(visibility), uploadedBy);
        StoredFile stored = fileService.upload(app, command);
        auditEvents.record("FILE_UPLOADED", app.code(), stored.id().toString(),
                "Upload concluído (" + stored.visibility() + ", " + stored.sizeBytes() + " bytes)");
        return ResponseEntity.created(URI.create(publicUrls.fileMetadataUrl(stored.id()))).body(toResponse(stored));
    }

    /** Lista os ficheiros da aplicação, mais recentes primeiro. */
    @GetMapping("/api/v1/files")
    public PageResponse<FileResponse> list(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @RequestParam(name = "folder", required = false) String folder,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size) {
        return PageResponse.from(fileService.list(app, folder, page, size).map(this::toResponse));
    }

    @GetMapping("/api/v1/files/{id}")
    public FileResponse metadata(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @PathVariable("id") UUID id) {
        return toResponse(fileService.get(app, id));
    }

    /** Conteúdo do ficheiro. {@code ?download=true} força o download em vez da abertura no browser. */
    @GetMapping("/api/v1/files/{id}/content")
    public ResponseEntity<Resource> content(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @PathVariable("id") UUID id,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        FileContent content = fileService.open(app, id);
        auditEvents.record("FILE_DOWNLOADED", app.code(), id.toString(), "Conteúdo consultado");
        return FileDownloads.respond(content, download, CacheControl.noCache().cachePrivate());
    }

    @DeleteMapping("/api/v1/files/{id}")
    public ResponseEntity<Void> delete(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @PathVariable("id") UUID id) {
        fileService.delete(app, id);
        auditEvents.record("FILE_DELETED", app.code(), id.toString(), "Ficheiro eliminado");
        return ResponseEntity.noContent().build();
    }

    /** Link temporário de leitura para um ficheiro privado (ex.: para um {@code <img>} ou um download). */
    @PostMapping("/api/v1/files/{id}/signed-url")
    public LinkResponse downloadLink(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @PathVariable("id") UUID id,
            @RequestBody(required = false) DownloadLinkRequest request) {
        Long ttl = request == null ? null : request.expiresInSeconds();
        SignedLink link = fileService.createDownloadLink(app, id, ttl, publicUrls.baseUrl());
        return new LinkResponse(link.url(), link.expiresAt());
    }

    /**
     * Link temporário de upload: o backend da aplicação pede-o e entrega-o ao browser, que
     * envia o ficheiro directamente para o serviço sem nunca conhecer a chave de API.
     */
    @PostMapping("/api/v1/upload-urls")
    public LinkResponse uploadLink(
            @RequestAttribute(AuthAttributes.APP) RegisteredApp app,
            @RequestBody(required = false) UploadLinkRequest request) {
        UploadLinkRequest body = request == null ? new UploadLinkRequest(null, null, null, null) : request;
        SignedLink link = fileService.createUploadLink(app, body.folder(), body.visibility(), body.uploadedBy(),
                body.expiresInSeconds(), publicUrls.baseUrl());
        return new LinkResponse(link.url(), link.expiresAt());
    }

    private FileResponse toResponse(StoredFile file) {
        return FileResponse.from(file, publicUrls.publicFileUrl(file.id()));
    }
}
