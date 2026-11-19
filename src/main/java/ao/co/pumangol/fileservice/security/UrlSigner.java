package ao.co.pumangol.fileservice.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assinatura HMAC-SHA256 usada nos links temporários de download e de upload.
 */
public final class UrlSigner {

    public static final int MIN_SECRET_LENGTH = 32;
    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public UrlSigner(String secret) {
        if (secret == null || secret.trim().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "Segredo de assinatura em falta ou demasiado curto: defina fileservice.security.signing-secret "
                            + "(variável FILESERVICE_SIGNING_SECRET) com pelo menos " + MIN_SECRET_LENGTH
                            + " caracteres aleatórios.");
        }
        this.key = new SecretKeySpec(secret.trim().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /**
     * Assina uma sequência de campos. Cada campo é precedido do seu tamanho, para que
     * {@code ("ab", "c")} e {@code ("a", "bc")} nunca produzam a mesma assinatura.
     */
    public String sign(String... parts) {
        Mac mac = newMac();
        for (String part : parts) {
            byte[] bytes = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
            mac.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
            mac.update(bytes);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal());
    }

    /** Verificação em tempo constante. */
    public boolean matches(String signature, String... parts) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        byte[] expected = sign(parts).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private Mac newMac() {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponível nesta JVM", e);
        }
    }
}
