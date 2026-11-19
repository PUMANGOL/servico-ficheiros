package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.apps.RegisteredApp;

/**
 * Autorização de upload obtida de um link assinado: a aplicação dona e as condições
 * (pasta, visibilidade, autor) que o backend fixou ao pedir o link.
 */
public record UploadGrant(RegisteredApp app, String folder, Visibility visibility, String uploadedBy) {
}
