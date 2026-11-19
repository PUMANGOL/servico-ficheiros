package ao.co.pumangol.fileservice.apps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.security.ApiKeys;
import ao.co.pumangol.fileservice.support.InMemoryAppRepository;
import ao.co.pumangol.fileservice.support.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AppServiceTest {

    private final InMemoryAppRepository repository = new InMemoryAppRepository();
    private final AppService service = new AppService(repository, new MutableClock(Instant.parse("2026-11-17T10:00:00Z")));

    @Test
    void createsAnApplicationAndReturnsTheKeyOnlyOnce() {
        CreatedApp created = service.create(new AppDefinition(" Portal-RH ", "Portal RH",
                List.of("https://RH.exemplo.co.ao/", "http://localhost:5173"), 5_000_000L,
                List.of("image/*", "Application/PDF"), null, null));

        RegisteredApp app = created.app();
        assertEquals("portal-rh", app.code());
        assertEquals("Portal RH", app.name());
        assertTrue(app.active());
        assertEquals(Set.of("https://rh.exemplo.co.ao", "http://localhost:5173"), app.allowedOrigins());
        assertEquals(List.of("image/*", "application/pdf"), app.allowedContentTypes());
        assertEquals(5_000_000L, app.maxFileSizeBytes());

        assertTrue(created.apiKey().startsWith(ApiKeys.PREFIX));
        assertEquals(ApiKeys.hash(created.apiKey()), app.apiKeyHash());
        assertNotEquals(created.apiKey(), app.apiKeyHash());
        assertEquals(app, repository.findByCode("portal-rh").orElseThrow());
    }

    @Test
    void rejectsInvalidDefinitions() {
        assertStatus(400, () -> service.create(null));
        assertStatus(400, () -> service.create(definition("A", "Nome")));
        assertStatus(400, () -> service.create(definition("com espaço", "Nome")));
        assertStatus(400, () -> service.create(definition("../etc", "Nome")));
        assertStatus(400, () -> service.create(definition("-inicio", "Nome")));
        assertStatus(400, () -> service.create(definition("portal", " ")));
        assertStatus(400, () -> service.create(new AppDefinition("portal", "Portal",
                List.of("https://app.exemplo.co.ao/caminho"), null, null, null, null)));
        assertStatus(400, () -> service.create(new AppDefinition("portal", "Portal",
                null, 0L, null, null, null)));
        assertStatus(400, () -> service.create(new AppDefinition("portal", "Portal",
                null, null, List.of("imagens"), null, null)));
    }

    @Test
    void rejectsDuplicatedCodes() {
        service.create(definition("portal", "Portal"));
        assertStatus(409, () -> service.create(definition("PORTAL", "Outro portal")));
    }

    @Test
    void authenticatesWithTheRightKeyOnly() {
        CreatedApp created = service.create(definition("portal", "Portal"));

        assertEquals("portal", service.authenticate("portal", created.apiKey()).code());
        assertEquals("portal", service.authenticate(" Portal ", created.apiKey()).code());

        ApiException wrongKey = assertStatus(401, () -> service.authenticate("portal", "fsk_errada"));
        ApiException unknownApp = assertStatus(401, () -> service.authenticate("nao-existe", created.apiKey()));
        assertEquals(wrongKey.getMessage(), unknownApp.getMessage());
        assertStatus(401, () -> service.authenticate(null, created.apiKey()));
        assertStatus(401, () -> service.authenticate("portal", null));
    }

    @Test
    void disabledApplicationsCannotAuthenticate() {
        CreatedApp created = service.create(definition("portal", "Portal"));
        service.update("portal", new AppDefinition(null, "Portal", null, null, null, null, false));

        ApiException error = assertStatus(403, () -> service.authenticate("portal", created.apiKey()));
        assertEquals("APP_DISABLED", error.getCode());
    }

    @Test
    void updateReplacesSettingsButKeepsCodeAndKey() {
        CreatedApp created = service.create(new AppDefinition("portal", "Portal",
                List.of("https://a.exemplo.co.ao"), 1000L, List.of("image/png"), 5000L, null));

        RegisteredApp updated = service.update("portal", new AppDefinition("ignorado", "Portal novo",
                List.of("https://b.exemplo.co.ao"), null, null, null, null));

        assertEquals("portal", updated.code());
        assertEquals("Portal novo", updated.name());
        assertEquals(Set.of("https://b.exemplo.co.ao"), updated.allowedOrigins());
        assertNull(updated.maxFileSizeBytes());
        assertTrue(updated.allowedContentTypes().isEmpty());
        assertEquals(created.app().apiKeyHash(), updated.apiKeyHash());
        assertEquals("portal", service.authenticate("portal", created.apiKey()).code());
    }

    @Test
    void rotatingTheKeyInvalidatesThePreviousOne() {
        CreatedApp created = service.create(definition("portal", "Portal"));
        CreatedApp rotated = service.rotateKey("portal");

        assertNotEquals(created.apiKey(), rotated.apiKey());
        assertStatus(401, () -> service.authenticate("portal", created.apiKey()));
        assertEquals("portal", service.authenticate("portal", rotated.apiKey()).code());
    }

    @Test
    void unknownApplicationIsNotFound() {
        assertStatus(404, () -> service.get("nao-existe"));
        assertStatus(404, () -> service.rotateKey("nao-existe"));
        assertFalse(service.list().stream().anyMatch(app -> app.code().equals("nao-existe")));
    }

    private static AppDefinition definition(String code, String name) {
        return new AppDefinition(code, name, null, null, null, null, null);
    }

    private static ApiException assertStatus(int expectedStatus, Runnable action) {
        ApiException error = assertThrows(ApiException.class, action::run);
        assertEquals(expectedStatus, error.getStatus(), error.getMessage());
        return error;
    }
}
