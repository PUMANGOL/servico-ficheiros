package ao.co.pumangol.fileservice.security;

import java.time.Instant;

/** Link temporário assinado e o momento em que deixa de ser válido. */
public record SignedLink(String url, Instant expiresAt) {
}
