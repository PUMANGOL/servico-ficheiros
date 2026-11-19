package ao.co.pumangol.fileservice.storage;

import java.nio.file.Path;

/** Determina o tipo real de um ficheiro, sem confiar no Content-Type declarado pelo cliente. */
public interface ContentTypeDetector {

    /**
     * @param file     ficheiro já gravado no disco
     * @param filename nome original, usado apenas como pista (ex.: distinguir .csv de .txt)
     * @return tipo MIME em minúsculas, sem parâmetros
     */
    String detect(Path file, String filename);
}
