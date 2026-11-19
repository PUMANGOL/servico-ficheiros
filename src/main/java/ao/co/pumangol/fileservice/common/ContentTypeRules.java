package ao.co.pumangol.fileservice.common;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Regras sobre tipos de conteúdo (MIME): lista de permitidos e o que pode ser mostrado inline.
 */
public final class ContentTypeRules {

    /** Usada quando nem a configuração global nem a aplicação definem uma lista própria. */
    public static final List<String> DEFAULT_ALLOWED = List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain",
            "text/csv",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    /**
     * Tipos que o browser pode abrir directamente sem risco de executar código no domínio
     * do serviço. Tudo o resto (HTML, SVG, XML, ...) é sempre servido como download.
     */
    private static final Set<String> INLINE_SAFE = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/avif",
            "application/pdf",
            "text/plain",
            "video/mp4",
            "video/webm",
            "audio/mpeg",
            "audio/ogg",
            "audio/wav");

    private static final String TOKEN = "[a-z0-9][a-z0-9!#$&^_.+-]*";
    private static final Pattern VALID_PATTERN =
            Pattern.compile("\\*/\\*|" + TOKEN + "/(\\*|" + TOKEN + ")");

    private ContentTypeRules() {
    }

    /** Minúsculas e sem parâmetros: {@code "Text/Plain; charset=UTF-8"} passa a {@code "text/plain"}. */
    public static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        String value = contentType;
        int semicolon = value.indexOf(';');
        if (semicolon >= 0) {
            value = value.substring(0, semicolon);
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    /** Aceita tipos exactos ({@code image/png}), famílias ({@code image/*}) e {@code *}{@code /*}. */
    public static boolean isValidPattern(String pattern) {
        return pattern != null && VALID_PATTERN.matcher(pattern).matches();
    }

    public static boolean isAllowed(String contentType, Collection<String> allowedPatterns) {
        String type = normalize(contentType);
        if (type.isEmpty() || allowedPatterns == null) {
            return false;
        }
        for (String raw : allowedPatterns) {
            String pattern = normalize(raw);
            if (pattern.equals("*/*") || pattern.equals(type)) {
                return true;
            }
            if (pattern.endsWith("/*") && type.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInlineSafe(String contentType) {
        return INLINE_SAFE.contains(normalize(contentType));
    }
}
