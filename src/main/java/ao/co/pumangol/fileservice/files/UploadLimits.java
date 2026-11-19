package ao.co.pumangol.fileservice.files;

import java.util.List;

/** Limites por omissão do serviço, usados quando a aplicação não define os seus. */
public record UploadLimits(long maxFileSizeBytes, List<String> allowedContentTypes) {

    public UploadLimits {
        if (maxFileSizeBytes <= 0) {
            throw new IllegalStateException("fileservice.limits.max-file-size tem de ser maior que zero.");
        }
        allowedContentTypes = List.copyOf(allowedContentTypes);
    }
}
