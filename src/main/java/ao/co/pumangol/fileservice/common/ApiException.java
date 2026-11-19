package ao.co.pumangol.fileservice.common;

/**
 * Erro de negócio com tradução directa para uma resposta HTTP.
 * O {@code code} é estável e serve para o frontend decidir o que mostrar;
 * a mensagem é para pessoas.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(400, code, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(401, "UNAUTHORIZED", message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(403, code, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(404, "NOT_FOUND", message);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(409, code, message);
    }

    public static ApiException payloadTooLarge(String code, String message) {
        return new ApiException(413, code, message);
    }

    public static ApiException unsupportedMediaType(String message) {
        return new ApiException(415, "CONTENT_TYPE_NOT_ALLOWED", message);
    }
}
