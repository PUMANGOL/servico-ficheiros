package ao.co.pumangol.fileservice.apps;

/** Aplicação acabada de criar (ou com a chave rodada) e a chave de API em claro, mostrada uma única vez. */
public record CreatedApp(RegisteredApp app, String apiKey) {
}
