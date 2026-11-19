package ao.co.pumangol.fileservice.apps;

/**
 * Resposta à criação de uma aplicação ou à rotação da chave.
 *
 * @param apiKey chave de API em claro. É a única vez que é mostrada: guardar num cofre de segredos.
 */
public record AppCreatedResponse(AppResponse application, String apiKey) {

    public static AppCreatedResponse from(CreatedApp created) {
        return new AppCreatedResponse(AppResponse.from(created.app()), created.apiKey());
    }
}
