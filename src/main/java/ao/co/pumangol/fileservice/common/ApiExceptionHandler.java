package ao.co.pumangol.fileservice.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Todas as respostas de erro seguem o formato RFC 9457 ({@code application/problem+json}):
 * <pre>
 *   { "type": "about:blank", "title": "Not Found", "status": 404,
 *     "detail": "Ficheiro não encontrado.", "code": "NOT_FOUND" }
 * </pre>
 * A classe base trata os erros normais do Spring MVC (JSON inválido, parâmetro em falta,
 * método não suportado, upload acima do tecto global, ...). Aqui tratamos os erros de
 * negócio e garantimos que um erro inesperado nunca expõe detalhes internos.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> onApiException(ApiException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatus());
        if (status.is5xxServerError()) {
            log.error("Erro do serviço [{}]: {}", ex.getCode(), ex.getMessage());
        }
        return problem(status, ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> onUnexpectedError(Exception ex) {
        if (ex instanceof MultipartException) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_MULTIPART",
                    "Pedido multipart inválido: envie o ficheiro num campo 'file' com multipart/form-data.");
        }
        log.error("Erro inesperado", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "Ocorreu um erro interno. Tente novamente; se persistir, contacte a equipa responsável.");
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String code, String detail) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(status.getReasonPhrase());
        body.setProperty("code", code);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
