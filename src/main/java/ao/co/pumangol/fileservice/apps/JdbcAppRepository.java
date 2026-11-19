package ao.co.pumangol.fileservice.apps;

import ao.co.pumangol.fileservice.common.ApiException;
import ao.co.pumangol.fileservice.security.Origins;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAppRepository implements AppRepository {

    private static final String SELECT = """
            select id, code, name, api_key_hash, api_key_prefix, max_file_size_bytes,
                   allowed_content_types, storage_quota_bytes, active, created_at, updated_at
              from fs_application
            """;

    private final JdbcClient jdbc;

    public JdbcAppRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void insert(RegisteredApp app) {
        try {
            jdbc.sql("""
                    insert into fs_application (id, code, name, api_key_hash, api_key_prefix, max_file_size_bytes,
                                                allowed_content_types, storage_quota_bytes, active, created_at, updated_at)
                    values (:id, :code, :name, :apiKeyHash, :apiKeyPrefix, :maxFileSizeBytes,
                            :allowedContentTypes, :storageQuotaBytes, :active, :createdAt, :updatedAt)
                    """)
                    .param("id", app.id())
                    .param("code", app.code())
                    .param("name", app.name())
                    .param("apiKeyHash", app.apiKeyHash())
                    .param("apiKeyPrefix", app.apiKeyPrefix())
                    .param("maxFileSizeBytes", app.maxFileSizeBytes(), Types.BIGINT)
                    .param("allowedContentTypes", joinContentTypes(app.allowedContentTypes()), Types.VARCHAR)
                    .param("storageQuotaBytes", app.storageQuotaBytes(), Types.BIGINT)
                    .param("active", app.active())
                    .param("createdAt", Timestamp.from(app.createdAt()))
                    .param("updatedAt", Timestamp.from(app.updatedAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("APP_CODE_EXISTS",
                    "Já existe uma aplicação com o código '" + app.code() + "'.");
        }
        insertOrigins(app.id(), app.allowedOrigins());
    }

    @Override
    @Transactional
    public void update(RegisteredApp app) {
        jdbc.sql("""
                update fs_application
                   set name = :name,
                       max_file_size_bytes = :maxFileSizeBytes,
                       allowed_content_types = :allowedContentTypes,
                       storage_quota_bytes = :storageQuotaBytes,
                       active = :active,
                       updated_at = :updatedAt
                 where id = :id
                """)
                .param("name", app.name())
                .param("maxFileSizeBytes", app.maxFileSizeBytes(), Types.BIGINT)
                .param("allowedContentTypes", joinContentTypes(app.allowedContentTypes()), Types.VARCHAR)
                .param("storageQuotaBytes", app.storageQuotaBytes(), Types.BIGINT)
                .param("active", app.active())
                .param("updatedAt", Timestamp.from(app.updatedAt()))
                .param("id", app.id())
                .update();

        jdbc.sql("delete from fs_application_origin where application_id = :id")
                .param("id", app.id())
                .update();
        insertOrigins(app.id(), app.allowedOrigins());
    }

    @Override
    public void updateApiKey(UUID id, String apiKeyHash, String apiKeyPrefix, Instant updatedAt) {
        jdbc.sql("""
                update fs_application
                   set api_key_hash = :apiKeyHash,
                       api_key_prefix = :apiKeyPrefix,
                       updated_at = :updatedAt
                 where id = :id
                """)
                .param("apiKeyHash", apiKeyHash)
                .param("apiKeyPrefix", apiKeyPrefix)
                .param("updatedAt", Timestamp.from(updatedAt))
                .param("id", id)
                .update();
    }

    @Override
    public Optional<RegisteredApp> findByCode(String code) {
        return jdbc.sql(SELECT + " where code = :code")
                .param("code", code)
                .query(JdbcAppRepository::mapRow)
                .optional()
                .map(this::withOrigins);
    }

    @Override
    public Optional<RegisteredApp> findById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id")
                .param("id", id)
                .query(JdbcAppRepository::mapRow)
                .optional()
                .map(this::withOrigins);
    }

    @Override
    public List<RegisteredApp> findAll() {
        List<RegisteredApp> apps = jdbc.sql(SELECT + " order by code")
                .query(JdbcAppRepository::mapRow)
                .list();

        Map<UUID, Set<String>> originsByApp = new HashMap<>();
        jdbc.sql("select application_id, origin from fs_application_origin")
                .query((rs, rowNum) -> Map.entry(rs.getObject("application_id", UUID.class), rs.getString("origin")))
                .list()
                .forEach(entry -> originsByApp.computeIfAbsent(entry.getKey(), key -> new TreeSet<>())
                        .add(entry.getValue()));

        List<RegisteredApp> result = new ArrayList<>(apps.size());
        for (RegisteredApp app : apps) {
            result.add(app.withAllowedOrigins(originsByApp.getOrDefault(app.id(), Set.of())));
        }
        return result;
    }

    @Override
    public boolean isOriginAllowedByAnyActiveApp(String normalizedOrigin) {
        Long matches = jdbc.sql("""
                select count(*)
                  from fs_application_origin o
                  join fs_application a on a.id = o.application_id
                 where a.active = true
                   and (o.origin = :origin or o.origin = :any)
                """)
                .param("origin", normalizedOrigin)
                .param("any", Origins.ANY)
                .query(Long.class)
                .single();
        return matches != null && matches > 0;
    }

    private void insertOrigins(UUID applicationId, Set<String> origins) {
        for (String origin : origins) {
            jdbc.sql("insert into fs_application_origin (application_id, origin) values (:applicationId, :origin)")
                    .param("applicationId", applicationId)
                    .param("origin", origin)
                    .update();
        }
    }

    private RegisteredApp withOrigins(RegisteredApp app) {
        List<String> origins = jdbc.sql("select origin from fs_application_origin where application_id = :id")
                .param("id", app.id())
                .query(String.class)
                .list();
        return app.withAllowedOrigins(new TreeSet<>(origins));
    }

    private static RegisteredApp mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RegisteredApp(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("api_key_hash"),
                rs.getString("api_key_prefix"),
                nullableLong(rs, "max_file_size_bytes"),
                splitContentTypes(rs.getString("allowed_content_types")),
                nullableLong(rs, "storage_quota_bytes"),
                rs.getBoolean("active"),
                Set.of(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static String joinContentTypes(List<String> contentTypes) {
        return contentTypes.isEmpty() ? null : String.join(",", contentTypes);
    }

    private static List<String> splitContentTypes(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(",")).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }
}
