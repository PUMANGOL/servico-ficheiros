package ao.co.pumangol.fileservice.security;

/** Nomes dos atributos do pedido onde os interceptores deixam o resultado da autenticação. */
public final class AuthAttributes {

    /** {@link ao.co.pumangol.fileservice.apps.RegisteredApp} autenticada por X-App-Id + X-Api-Key. */
    public static final String APP = "fileservice.app";

    /** {@link ao.co.pumangol.fileservice.files.UploadGrant} obtido de um link de upload assinado. */
    public static final String UPLOAD_GRANT = "fileservice.uploadGrant";

    private AuthAttributes() {
    }
}
