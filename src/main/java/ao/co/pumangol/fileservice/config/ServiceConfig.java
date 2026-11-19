package ao.co.pumangol.fileservice.config;

import ao.co.pumangol.fileservice.apps.AppRepository;
import ao.co.pumangol.fileservice.apps.AppService;
import ao.co.pumangol.fileservice.files.FileRepository;
import ao.co.pumangol.fileservice.files.FileService;
import ao.co.pumangol.fileservice.files.UploadLimits;
import ao.co.pumangol.fileservice.security.SignedUrlService;
import ao.co.pumangol.fileservice.security.UrlSigner;
import ao.co.pumangol.fileservice.storage.ContentTypeDetector;
import ao.co.pumangol.fileservice.storage.FileStorage;
import ao.co.pumangol.fileservice.storage.LocalFileStorage;
import ao.co.pumangol.fileservice.storage.TikaContentTypeDetector;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Ligação das peças do núcleo. As classes de negócio não dependem do Spring: são criadas
 * aqui, o que as mantém testáveis sem levantar o contexto da aplicação.
 * <p>
 * Uma configuração inválida (segredo de assinatura em falta, pasta sem permissões) impede
 * o arranque com uma mensagem explícita, em vez de falhar mais tarde no primeiro pedido.
 */
@Configuration
public class ServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public FileStorage fileStorage(FileServiceProperties properties) {
        return new LocalFileStorage(Path.of(properties.storage().basePath()));
    }

    @Bean
    public ContentTypeDetector contentTypeDetector() {
        return new TikaContentTypeDetector();
    }

    @Bean
    public UrlSigner urlSigner(FileServiceProperties properties) {
        return new UrlSigner(properties.security().signingSecret());
    }

    @Bean
    public SignedUrlService signedUrlService(UrlSigner urlSigner, FileServiceProperties properties, Clock clock) {
        return new SignedUrlService(urlSigner, properties.signedUrls().defaultTtl(),
                properties.signedUrls().maxTtl(), clock);
    }

    @Bean
    public AppService appService(AppRepository appRepository, Clock clock) {
        return new AppService(appRepository, clock);
    }

    @Bean
    public FileService fileService(FileRepository fileRepository, AppRepository appRepository,
                                   FileStorage fileStorage, ContentTypeDetector contentTypeDetector,
                                   SignedUrlService signedUrlService, FileServiceProperties properties, Clock clock) {
        UploadLimits limits = new UploadLimits(properties.limits().maxFileSize().toBytes(),
                properties.limits().allowedContentTypes());
        return new FileService(fileRepository, appRepository, fileStorage, contentTypeDetector,
                signedUrlService, limits, clock);
    }
}
