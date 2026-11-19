package ao.co.pumangol.fileservice.common;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Leitura da query string em bruto, sem tocar no corpo do pedido.
 * <p>
 * Num upload multipart, {@code request.getParameter(...)} obriga o servidor a ler o corpo
 * inteiro. Para validar a assinatura de um link de upload antes de aceitar o ficheiro,
 * lemos os parâmetros directamente do URL.
 */
public final class QueryString {

    private QueryString() {
    }

    /** Devolve o primeiro valor de cada parâmetro. Pares mal formados são ignorados. */
    public static Map<String, String> parse(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String rawName = equals >= 0 ? pair.substring(0, equals) : pair;
            String rawValue = equals >= 0 ? pair.substring(equals + 1) : "";
            try {
                String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
                String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
                params.putIfAbsent(name, value);
            } catch (IllegalArgumentException malformed) {
                // sequência %xx inválida: ignoramos o par
            }
        }
        return params;
    }
}
