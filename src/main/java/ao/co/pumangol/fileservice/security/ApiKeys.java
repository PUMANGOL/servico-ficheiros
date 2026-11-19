package ao.co.pumangol.fileservice.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Geração e verificação das chaves de API das aplicações.
 * <p>
 * A chave é aleatória e de alta entropia (256 bits), por isso guardamos apenas o seu
 * SHA-256: não é possível recuperar a chave a partir da base de dados e a verificação
 * é rápida. A chave em claro só é mostrada uma vez, na criação ou na rotação.
 */
public final class ApiKeys {

    public static final String PREFIX = "fsk_";
    private static final int RANDOM_BYTES = 32;
    private static final int VISIBLE_PREFIX_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeys() {
    }

    public static String generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 em hexadecimal (64 caracteres). */
    public static String hash(String apiKey) {
        return HexFormat.of().formatHex(sha256(apiKey));
    }

    /** Comparação em tempo constante entre a chave apresentada e o hash guardado. */
    public static boolean matches(String presentedKey, String storedHashHex) {
        if (presentedKey == null || storedHashHex == null) {
            return false;
        }
        byte[] presented = hash(presentedKey).getBytes(StandardCharsets.US_ASCII);
        byte[] stored = storedHashHex.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(presented, stored);
    }

    /** Comparação em tempo constante entre dois segredos em claro (ex.: chave de administração). */
    public static boolean secretsEqual(String presented, String expected) {
        if (presented == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(sha256(presented), sha256(expected));
    }

    /** Início da chave, guardado para a identificar em listagens e registos sem a revelar. */
    public static String visiblePrefix(String apiKey) {
        return apiKey.length() <= VISIBLE_PREFIX_LENGTH ? apiKey : apiKey.substring(0, VISIBLE_PREFIX_LENGTH);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível nesta JVM", e);
        }
    }
}
