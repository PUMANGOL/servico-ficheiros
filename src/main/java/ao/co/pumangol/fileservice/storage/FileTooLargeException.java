package ao.co.pumangol.fileservice.storage;

/** O conteúdo recebido ultrapassou o limite permitido. */
public class FileTooLargeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final long maxBytes;

    public FileTooLargeException(long maxBytes) {
        super("Ficheiro acima do limite de " + maxBytes + " bytes");
        this.maxBytes = maxBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
