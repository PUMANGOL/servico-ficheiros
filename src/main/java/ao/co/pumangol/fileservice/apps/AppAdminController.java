package ao.co.pumangol.fileservice.apps;

import ao.co.pumangol.fileservice.dashboard.AuditEventService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestão das aplicações clientes. Protegido por {@code X-Admin-Key}
 * (ver {@link ao.co.pumangol.fileservice.security.AdminKeyInterceptor}).
 */
@RestController
@RequestMapping("/api/v1/admin/applications")
public class AppAdminController {

    private final AppService appService;
    private final AuditEventService auditEvents;

    public AppAdminController(AppService appService, AuditEventService auditEvents) {
        this.appService = appService;
        this.auditEvents = auditEvents;
    }

    /** Regista uma aplicação e devolve a sua chave de API (mostrada apenas aqui). */
    @PostMapping
    public ResponseEntity<AppCreatedResponse> create(@RequestBody AppDefinition definition) {
        CreatedApp created = appService.create(definition);
        auditEvents.record("APP_CREATED", created.app().code(), created.app().id().toString(),
                "Aplicação registada");
        URI location = URI.create("/api/v1/admin/applications/" + created.app().code());
        return ResponseEntity.created(location).body(AppCreatedResponse.from(created));
    }

    @GetMapping
    public List<AppResponse> list() {
        return appService.list().stream().map(AppResponse::from).toList();
    }

    @GetMapping("/{code}")
    public AppResponse get(@PathVariable("code") String code) {
        return AppResponse.from(appService.get(code));
    }

    /** Substitui nome, origens, limites e estado. O código e a chave não mudam. */
    @PutMapping("/{code}")
    public AppResponse update(@PathVariable("code") String code, @RequestBody AppDefinition definition) {
        RegisteredApp updated = appService.update(code, definition);
        auditEvents.record("APP_UPDATED", updated.code(), updated.id().toString(),
                updated.active() ? "Configuração actualizada" : "Aplicação desactivada");
        return AppResponse.from(updated);
    }

    /** Gera uma chave nova; a anterior deixa de funcionar de imediato. */
    @PostMapping("/{code}/rotate-key")
    public AppCreatedResponse rotateKey(@PathVariable("code") String code) {
        CreatedApp rotated = appService.rotateKey(code);
        auditEvents.record("API_KEY_ROTATED", rotated.app().code(), rotated.app().id().toString(),
                "Chave de API rodada");
        return AppCreatedResponse.from(rotated);
    }
}
