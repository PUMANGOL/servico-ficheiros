package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.common.ApiException;
import java.util.Locale;

public enum Visibility {

    /** Só acessível com as credenciais da aplicação ou através de um link temporário assinado. */
    PRIVATE,

    /** Acessível por URL estável, sem credenciais (ex.: imagens de um site público). */
    PUBLIC;

    /** Valor vazio ou ausente significa {@link #PRIVATE}. */
    public static Visibility parse(String value) {
        if (value == null || value.isBlank()) {
            return PRIVATE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_VISIBILITY", "Visibilidade inválida: use PRIVATE ou PUBLIC.");
        }
    }
}
