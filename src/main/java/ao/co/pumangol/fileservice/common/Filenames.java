package ao.co.pumangol.fileservice.common;

import java.text.Normalizer;

/**
 * Limpeza do nome original do ficheiro. O nome nunca é usado para construir caminhos no disco
 * (isso é feito com UUID); serve apenas para metadados e para o cabeçalho Content-Disposition.
 */
public final class Filenames {

    public static final String DEFAULT_NAME = "ficheiro";
    public static final int MAX_LENGTH = 255;

    private Filenames() {
    }

    public static String sanitize(String original) {
        if (original == null) {
            return DEFAULT_NAME;
        }
        String name = Normalizer.normalize(original, Normalizer.Form.NFC);

        // Alguns browsers antigos enviam o caminho completo: ficamos só com o último segmento.
        int lastSeparator = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSeparator >= 0) {
            name = name.substring(lastSeparator + 1);
        }

        StringBuilder cleaned = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            if (Character.isISOControl(cp) || isReserved(cp)) {
                cleaned.append('_');
            } else {
                cleaned.appendCodePoint(cp);
            }
        });

        name = stripDotsAndSpaces(cleaned.toString());
        if (name.isEmpty()) {
            return DEFAULT_NAME;
        }
        return truncateKeepingExtension(name);
    }

    private static boolean isReserved(int codePoint) {
        return switch (codePoint) {
            case ':', '*', '?', '"', '<', '>', '|' -> true;
            default -> false;
        };
    }

    private static String stripDotsAndSpaces(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isDotOrSpace(value.charAt(start))) {
            start++;
        }
        while (end > start && isDotOrSpace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isDotOrSpace(char c) {
        return c == '.' || Character.isWhitespace(c);
    }

    private static String truncateKeepingExtension(String name) {
        if (name.length() <= MAX_LENGTH) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        String extension = (dot > 0 && name.length() - dot <= 16) ? name.substring(dot) : "";
        String base = name.substring(0, MAX_LENGTH - extension.length());
        // Evita cortar um par de substitutos (emoji, etc.) a meio.
        if (Character.isHighSurrogate(base.charAt(base.length() - 1))) {
            base = base.substring(0, base.length() - 1);
        }
        return base + extension;
    }
}
