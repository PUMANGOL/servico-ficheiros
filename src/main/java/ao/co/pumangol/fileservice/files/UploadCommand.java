package ao.co.pumangol.fileservice.files;

import java.io.IOException;
import java.io.InputStream;

/**
 * Pedido de upload, independente da camada web.
 *
 * @param declaredSize tamanho anunciado pelo cliente (permite recusar cedo); o tamanho real é
 *                     sempre medido ao gravar
 */
public record UploadCommand(
        String originalFilename,
        long declaredSize,
        ContentSource content,
        String folder,
        Visibility visibility,
        String uploadedBy) {

    @FunctionalInterface
    public interface ContentSource {
        InputStream open() throws IOException;
    }
}
