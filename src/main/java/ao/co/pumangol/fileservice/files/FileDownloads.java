package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.common.ContentTypeRules;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Monta a resposta HTTP de um download.
 * <ul>
 *   <li>O conteúdo é enviado em streaming a partir do disco; o Spring trata dos pedidos
 *       parciais ({@code Range}) e condicionais ({@code If-None-Match}).</li>
 *   <li>Só tipos seguros (imagens, PDF, texto simples, áudio, vídeo) são mostrados inline;
 *       os restantes são sempre descarregados, para que nenhum ficheiro enviado por
 *       utilizadores possa executar código no domínio do serviço.</li>
 *   <li>{@code X-Content-Type-Options: nosniff} impede o browser de adivinhar outro tipo.</li>
 * </ul>
 */
final class FileDownloads {

    private FileDownloads() {
    }

    static ResponseEntity<Resource> respond(FileContent content, boolean forceDownload, CacheControl cacheControl) {
        StoredFile file = content.file();
        boolean inline = !forceDownload && ContentTypeRules.isInlineSafe(file.contentType());
        ContentDisposition disposition = ContentDisposition.builder(inline ? "inline" : "attachment")
                .filename(file.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(mediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(cacheControl)
                .eTag("\"" + file.checksumSha256() + "\"")
                .lastModified(file.createdAt())
                .body(new FileSystemResource(content.path()));
    }

    private static MediaType mediaType(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
