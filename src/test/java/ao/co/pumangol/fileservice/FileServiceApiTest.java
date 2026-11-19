package ao.co.pumangol.fileservice;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Teste de ponta a ponta: levanta a aplicação completa (Tomcat, filtros, interceptores,
 * Flyway, Tika) numa porta aleatória e fala com ela por HTTP, como um cliente real.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FileServiceApiTest {

    private static final String ADMIN_KEY = "chave-admin-de-teste-0123456789";
    private static final Path STORAGE = createStorageDirectory();

    private static final byte[] PNG = pngBytes();
    private static final byte[] HTML = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);

    @Value("${local.server.port}")
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("fileservice.storage.base-path", STORAGE::toString);
    }

    // ------------------------------------------------------------------ básico

    @Test
    void healthEndpointIsOpen() throws Exception {
        HttpResponse<String> response = send(get("/actuator/health"));
        assertEquals(200, response.statusCode());
        assertEquals("UP", JsonPath.read(response.body(), "$.status"));
    }

    @Test
    void adminApiRequiresTheAdminKey() throws Exception {
        String body = "{\"code\":\"sem-chave\",\"name\":\"Sem chave\"}";

        HttpResponse<String> missing = send(request("/api/v1/admin/applications")
                .header("Content-Type", "application/json").POST(BodyPublishers.ofString(body)));
        assertProblem(missing, 401, "UNAUTHORIZED");

        HttpResponse<String> wrong = send(request("/api/v1/admin/applications")
                .header("X-Admin-Key", "chave-errada")
                .header("Content-Type", "application/json").POST(BodyPublishers.ofString(body)));
        assertProblem(wrong, 401, "UNAUTHORIZED");
    }

    @Test
    void adminApiManagesApplications() throws Exception {
        App app = createApp("https://gestao.exemplo.co.ao");

        HttpResponse<String> fetched = send(get("/api/v1/admin/applications/" + app.code()).header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, fetched.statusCode());
        assertEquals(app.code(), JsonPath.read(fetched.body(), "$.code"));
        assertEquals(List.of("https://gestao.exemplo.co.ao"), JsonPath.read(fetched.body(), "$.allowedOrigins"));
        assertFalse(fetched.body().contains("apiKeyHash"), "o hash da chave nunca sai na API");

        HttpResponse<String> duplicate = send(request("/api/v1/admin/applications")
                .header("X-Admin-Key", ADMIN_KEY).header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"code\":\"" + app.code() + "\",\"name\":\"Repetida\"}")));
        assertProblem(duplicate, 409, "APP_CODE_EXISTS");

        HttpResponse<String> rotated = send(request("/api/v1/admin/applications/" + app.code() + "/rotate-key")
                .header("X-Admin-Key", ADMIN_KEY).POST(BodyPublishers.noBody()));
        assertEquals(200, rotated.statusCode());
        String newKey = JsonPath.read(rotated.body(), "$.apiKey");

        assertProblem(send(get("/api/v1/files").header("X-App-Id", app.code()).header("X-Api-Key", app.apiKey())),
                401, "UNAUTHORIZED");
        assertEquals(200, send(get("/api/v1/files").header("X-App-Id", app.code()).header("X-Api-Key", newKey)).statusCode());
    }

    @Test
    void dashboardEndpointsRequireAdminKeyAndExposeOperationalMetrics() throws Exception {
        assertProblem(send(get("/api/v1/admin/dashboard/metrics")), 401, "UNAUTHORIZED");

        App app = createApp();
        uploadPng(app);

        HttpResponse<String> health = send(get("/api/v1/admin/dashboard/health")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, health.statusCode(), health.body());
        assertEquals("UP", JsonPath.read(health.body(), "$.status"));

        HttpResponse<String> metrics = send(get("/api/v1/admin/dashboard/metrics")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, metrics.statusCode(), metrics.body());
        assertTrue(((Number) JsonPath.read(metrics.body(), "$.activeFiles")).longValue() >= 1);
        assertTrue(((Number) JsonPath.read(metrics.body(), "$.storageBytes")).longValue() >= PNG.length);

        HttpResponse<String> activity = send(get("/api/v1/admin/dashboard/activity?limit=20")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, activity.statusCode(), activity.body());
        assertTrue(activity.body().contains("FILE_UPLOADED"));
        assertTrue(activity.body().contains(app.code()));

        HttpResponse<String> daily = send(get("/api/v1/admin/dashboard/daily?days=14")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, daily.statusCode(), daily.body());
        assertTrue(daily.body().contains("\"uploads\""));
    }

    @Test
    void adminCanFilterAndViewStoredFiles() throws Exception {
        App app = createApp();
        String id = uploadPng(app);

        assertProblem(send(get("/api/v1/admin/dashboard/files")), 401, "UNAUTHORIZED");

        HttpResponse<String> listed = send(get("/api/v1/admin/dashboard/files?applicationCode=" + app.code()
                + "&q=imagem&visibility=PRIVATE")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, listed.statusCode(), listed.body());
        assertEquals(1, ((Number) JsonPath.read(listed.body(), "$.totalItems")).intValue());
        assertEquals(id, JsonPath.read(listed.body(), "$.items[0].id"));
        assertEquals(app.code(), JsonPath.read(listed.body(), "$.items[0].applicationCode"));

        HttpResponse<byte[]> content = sendBytes(get("/api/v1/admin/dashboard/files/" + id + "/content")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, content.statusCode());
        assertArrayEquals(PNG, content.body());
        assertTrue(content.headers().firstValue("Content-Disposition").orElse("").startsWith("inline"));

        HttpResponse<byte[]> forcedDownload = sendBytes(get("/api/v1/admin/dashboard/files/" + id + "/content?download=true")
                .header("X-Admin-Key", ADMIN_KEY));
        assertEquals(200, forcedDownload.statusCode());
        assertTrue(forcedDownload.headers().firstValue("Content-Disposition").orElse("").startsWith("attachment"));
    }

    // ---------------------------------------------------------------- ficheiros

    @Test
    void uploadDownloadListAndDelete() throws Exception {
        App app = createApp();

        HttpResponse<String> uploaded = send(upload("/api/v1/files", "Logótipo.png", PNG,
                Map.of("folder", "imagens/logos", "uploadedBy", "u-42")).headers(app.headers()));
        assertEquals(201, uploaded.statusCode(), uploaded.body());
        String id = JsonPath.read(uploaded.body(), "$.id");
        assertEquals("Logótipo.png", JsonPath.read(uploaded.body(), "$.filename"));
        assertEquals("image/png", JsonPath.read(uploaded.body(), "$.contentType"));
        assertEquals(PNG.length, ((Number) JsonPath.read(uploaded.body(), "$.sizeBytes")).intValue());
        assertEquals("PRIVATE", JsonPath.read(uploaded.body(), "$.visibility"));
        assertEquals("imagens/logos", JsonPath.read(uploaded.body(), "$.folder"));
        assertNull(JsonPath.read(uploaded.body(), "$.publicUrl"));
        assertTrue(uploaded.headers().firstValue("Location").orElse("").endsWith("/api/v1/files/" + id));

        HttpResponse<String> metadata = send(get("/api/v1/files/" + id).headers(app.headers()));
        assertEquals(200, metadata.statusCode());
        assertEquals("u-42", JsonPath.read(metadata.body(), "$.uploadedBy"));

        HttpResponse<byte[]> content = sendBytes(get("/api/v1/files/" + id + "/content").headers(app.headers()));
        assertEquals(200, content.statusCode());
        assertArrayEquals(PNG, content.body());
        assertEquals("image/png", content.headers().firstValue("Content-Type").orElse(""));
        assertTrue(content.headers().firstValue("Content-Disposition").orElse("").startsWith("inline"));
        assertEquals("nosniff", content.headers().firstValue("X-Content-Type-Options").orElse(""));
        String etag = content.headers().firstValue("ETag").orElse("");
        assertEquals("\"" + JsonPath.read(metadata.body(), "$.checksumSha256") + "\"", etag);

        HttpResponse<String> list = send(get("/api/v1/files?folder=imagens/logos").headers(app.headers()));
        assertEquals(200, list.statusCode());
        assertEquals(1, ((Number) JsonPath.read(list.body(), "$.totalItems")).intValue());
        assertEquals(id, JsonPath.read(list.body(), "$.items[0].id"));

        assertEquals(204, send(request("/api/v1/files/" + id).DELETE().headers(app.headers())).statusCode());
        assertProblem(send(get("/api/v1/files/" + id).headers(app.headers())), 404, "NOT_FOUND");
        assertProblem(send(get("/api/v1/files/" + id + "/content").headers(app.headers())), 404, "NOT_FOUND");
    }

    @Test
    void conditionalAndRangeRequestsAreSupported() throws Exception {
        App app = createApp();
        String id = uploadPng(app);

        HttpResponse<byte[]> full = sendBytes(get("/api/v1/files/" + id + "/content").headers(app.headers()));
        String etag = full.headers().firstValue("ETag").orElseThrow();

        HttpResponse<byte[]> notModified = sendBytes(get("/api/v1/files/" + id + "/content")
                .headers(app.headers()).header("If-None-Match", etag));
        assertEquals(304, notModified.statusCode());

        HttpResponse<byte[]> partial = sendBytes(get("/api/v1/files/" + id + "/content")
                .headers(app.headers()).header("Range", "bytes=0-7"));
        assertEquals(206, partial.statusCode());
        assertEquals(8, partial.body().length);
    }

    @Test
    void requiresValidCredentials() throws Exception {
        App app = createApp();

        assertProblem(send(get("/api/v1/files")), 401, "UNAUTHORIZED");
        assertProblem(send(get("/api/v1/files").header("X-App-Id", app.code()).header("X-Api-Key", "fsk_errada")),
                401, "UNAUTHORIZED");
        assertProblem(send(get("/api/v1/files").header("X-App-Id", "nao-existe").header("X-Api-Key", app.apiKey())),
                401, "UNAUTHORIZED");
        assertProblem(send(upload("/api/v1/files", "a.png", PNG, Map.of())), 401, "UNAUTHORIZED");
    }

    @Test
    void applicationsCannotSeeEachOthersFiles() throws Exception {
        App owner = createApp();
        App other = createApp();
        String id = uploadPng(owner);

        assertProblem(send(get("/api/v1/files/" + id).headers(other.headers())), 404, "NOT_FOUND");
        assertProblem(send(get("/api/v1/files/" + id + "/content").headers(other.headers())), 404, "NOT_FOUND");
        assertProblem(send(request("/api/v1/files/" + id).DELETE().headers(other.headers())), 404, "NOT_FOUND");
        assertEquals(200, send(get("/api/v1/files/" + id).headers(owner.headers())).statusCode());
    }

    @Test
    void rejectsContentThatDoesNotMatchAnAllowedType() throws Exception {
        App app = createApp();

        // Um HTML com extensão .png: o tipo é detectado pelo conteúdo, não pelo nome.
        assertProblem(send(upload("/api/v1/files", "foto.png", HTML, Map.of()).headers(app.headers())),
                415, "CONTENT_TYPE_NOT_ALLOWED");
        assertProblem(send(upload("/api/v1/files", "vazio.png", new byte[0], Map.of()).headers(app.headers())),
                400, "EMPTY_FILE");
        assertProblem(send(upload("/api/v1/files", "grande.png", pad(PNG, 1024 * 1024 + 1), Map.of()).headers(app.headers())),
                413, "FILE_TOO_LARGE");
        assertProblem(send(upload("/api/v1/files", "a.png", PNG, Map.of("folder", "../fora")).headers(app.headers())),
                400, "INVALID_FOLDER");
    }

    // ------------------------------------------------------- links temporários

    @Test
    void signedLinkGivesTemporaryAccessToAPrivateFile() throws Exception {
        App app = createApp();
        String id = uploadPng(app);

        HttpResponse<String> link = send(request("/api/v1/files/" + id + "/signed-url").headers(app.headers())
                .header("Content-Type", "application/json").POST(BodyPublishers.ofString("{\"expiresInSeconds\":60}")));
        assertEquals(200, link.statusCode(), link.body());
        String url = JsonPath.read(link.body(), "$.url");
        assertNotNull(JsonPath.read(link.body(), "$.expiresAt"));
        assertTrue(url.startsWith(baseUrl() + "/api/v1/public/files/" + id + "?"), url);

        // Sem credenciais nenhumas: basta o link.
        HttpResponse<byte[]> viaLink = sendBytes(HttpRequest.newBuilder(URI.create(url)).GET());
        assertEquals(200, viaLink.statusCode());
        assertArrayEquals(PNG, viaLink.body());

        assertProblem(send(HttpRequest.newBuilder(URI.create(url + "x")).GET()), 403, "INVALID_SIGNATURE");
        assertProblem(send(get("/api/v1/public/files/" + id)), 404, "NOT_FOUND");
        assertProblem(send(get("/api/v1/public/files/" + UUID.randomUUID())), 404, "NOT_FOUND");
    }

    @Test
    void signedUploadLinkLetsTheBrowserUploadWithoutTheApiKey() throws Exception {
        App app = createApp();

        HttpResponse<String> link = send(request("/api/v1/upload-urls").headers(app.headers())
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"folder\":\"avatars\",\"visibility\":\"PUBLIC\",\"uploadedBy\":\"u-7\"}")));
        assertEquals(200, link.statusCode(), link.body());
        String uploadUrl = JsonPath.read(link.body(), "$.url");

        // O "browser": sem cabeçalhos de autenticação.
        HttpResponse<String> uploaded = send(upload(URI.create(uploadUrl), "eu.png", PNG, Map.of("folder", "tentativa-de-troca")));
        assertEquals(201, uploaded.statusCode(), uploaded.body());
        assertEquals("avatars", JsonPath.read(uploaded.body(), "$.folder"));
        assertEquals("PUBLIC", JsonPath.read(uploaded.body(), "$.visibility"));
        assertEquals("u-7", JsonPath.read(uploaded.body(), "$.uploadedBy"));

        // Ficheiro PUBLIC: URL estável, sem assinatura.
        String publicUrl = JsonPath.read(uploaded.body(), "$.publicUrl");
        HttpResponse<byte[]> viaPublicUrl = sendBytes(HttpRequest.newBuilder(URI.create(publicUrl)).GET());
        assertEquals(200, viaPublicUrl.statusCode());
        assertArrayEquals(PNG, viaPublicUrl.body());
        assertTrue(viaPublicUrl.headers().firstValue("Cache-Control").orElse("").contains("public"));

        // Alterar as condições do link invalida a assinatura; sem link não há upload.
        String tampered = uploadUrl.replace("folder=avatars", "folder=outra");
        assertProblem(send(upload(URI.create(tampered), "eu.png", PNG, Map.of())), 403, "INVALID_SIGNATURE");
        assertProblem(send(upload("/api/v1/public/uploads", "eu.png", PNG, Map.of())), 403, "INVALID_SIGNATURE");
    }

    // -------------------------------------------------------------------- CORS

    @Test
    void corsIsRestrictedToTheRegisteredOrigins() throws Exception {
        String origin = "https://" + UUID.randomUUID() + ".exemplo.co.ao";
        App app = createApp(origin);
        App other = createApp();

        HttpResponse<String> preflight = send(request("/api/v1/files")
                .method("OPTIONS", BodyPublishers.noBody())
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-app-id, x-api-key, content-type"));
        assertEquals(200, preflight.statusCode());
        assertEquals(origin, preflight.headers().firstValue("Access-Control-Allow-Origin").orElse(""));

        HttpResponse<String> unknownOrigin = send(request("/api/v1/files")
                .method("OPTIONS", BodyPublishers.noBody())
                .header("Origin", "https://intruso.exemplo.com")
                .header("Access-Control-Request-Method", "POST"));
        assertEquals(403, unknownOrigin.statusCode());

        // Pedido real a partir da origem registada: passa e traz os cabeçalhos CORS.
        HttpResponse<String> allowed = send(get("/api/v1/files").headers(app.headers()).header("Origin", origin));
        assertEquals(200, allowed.statusCode());
        assertEquals(origin, allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(""));

        // A origem pertence a outra aplicação: recusado, mesmo com credenciais válidas.
        HttpResponse<String> foreign = send(get("/api/v1/files").headers(other.headers()).header("Origin", origin));
        assertProblem(foreign, 403, "ORIGIN_NOT_ALLOWED");
    }

    // ------------------------------------------------------------------ apoio

    private record App(String code, String apiKey) {
        String[] headers() {
            return new String[] {"X-App-Id", code, "X-Api-Key", apiKey};
        }
    }

    private App createApp(String... origins) throws Exception {
        String code = "app-" + UUID.randomUUID().toString().substring(0, 12);
        StringBuilder originsJson = new StringBuilder();
        for (String origin : origins) {
            originsJson.append(originsJson.isEmpty() ? "" : ",").append('"').append(origin).append('"');
        }
        String body = "{\"code\":\"" + code + "\",\"name\":\"Aplicação de teste\",\"allowedOrigins\":[" + originsJson + "]}";

        HttpResponse<String> response = send(request("/api/v1/admin/applications")
                .header("X-Admin-Key", ADMIN_KEY)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body)));
        assertEquals(201, response.statusCode(), response.body());
        assertEquals(code, JsonPath.read(response.body(), "$.application.code"));
        String apiKey = JsonPath.read(response.body(), "$.apiKey");
        assertTrue(apiKey.startsWith("fsk_"));
        return new App(code, apiKey);
    }

    private String uploadPng(App app) throws Exception {
        HttpResponse<String> response = send(upload("/api/v1/files", "imagem.png", PNG, Map.of()).headers(app.headers()));
        assertEquals(201, response.statusCode(), response.body());
        return JsonPath.read(response.body(), "$.id");
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl() + path));
    }

    private HttpRequest.Builder get(String path) {
        return request(path).GET();
    }

    private HttpRequest.Builder upload(String path, String filename, byte[] content, Map<String, String> fields) {
        return upload(URI.create(baseUrl() + path), filename, content, fields);
    }

    private static HttpRequest.Builder upload(URI uri, String filename, byte[] content, Map<String, String> fields) {
        String boundary = "----teste" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        fields.forEach((name, value) -> write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n"));
        write(body, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n");
        body.writeBytes(content);
        write(body, "\r\n--" + boundary + "--\r\n");
        return HttpRequest.newBuilder(uri)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(BodyPublishers.ofByteArray(body.toByteArray()));
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return http.send(builder.build(), BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest.Builder builder) throws IOException, InterruptedException {
        return http.send(builder.build(), BodyHandlers.ofByteArray());
    }

    private static void assertProblem(HttpResponse<String> response, int expectedStatus, String expectedCode) {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"),
                "resposta de erro em application/problem+json: " + response.headers().map());
        assertEquals(expectedCode, JsonPath.read(response.body(), "$.code"), response.body());
        assertEquals(expectedStatus, ((Number) JsonPath.read(response.body(), "$.status")).intValue());
    }

    private static byte[] pngBytes() {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        return pad(signature, 256);
    }

    private static byte[] pad(byte[] head, int size) {
        byte[] result = new byte[size];
        System.arraycopy(head, 0, result, 0, Math.min(head.length, size));
        return result;
    }

    private static Path createStorageDirectory() {
        try {
            return Files.createTempDirectory("fileservice-teste-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
