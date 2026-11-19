package ao.co.pumangol.fileservice.security;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Origens (CORS) autorizadas por aplicação, no formato {@code esquema://anfitrião[:porta]}.
 * O valor especial {@code *} aceita qualquer origem e só deve ser usado em desenvolvimento.
 */
public final class Origins {

    public static final String ANY = "*";
    public static final int MAX_LENGTH = 255;

    private static final Pattern VALID =
            Pattern.compile("[a-z][a-z0-9+.-]*://[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?");

    private Origins() {
    }

    /** Minúsculas, sem espaços e sem barra final. Devolve {@code null} para valores vazios. */
    public static String normalize(String origin) {
        if (origin == null) {
            return null;
        }
        String value = origin.trim().toLowerCase(Locale.ROOT);
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? null : value;
    }

    /** Recebe um valor já normalizado. */
    public static boolean isValid(String normalizedOrigin) {
        if (normalizedOrigin == null || normalizedOrigin.length() > MAX_LENGTH) {
            return false;
        }
        return ANY.equals(normalizedOrigin) || VALID.matcher(normalizedOrigin).matches();
    }
}
