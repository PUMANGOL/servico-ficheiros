package ao.co.pumangol.fileservice.files;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcFileRepository implements FileRepository {

    private static final String SELECT = """
            select id, application_id, original_filename, storage_path, content_type, size_bytes,
                   checksum_sha256, visibility, folder, uploaded_by, created_at, deleted_at
              from fs_file
            """;

    private final JdbcClient jdbc;

    public JdbcFileRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(StoredFile file) {
        jdbc.sql("""
                insert into fs_file (id, application_id, original_filename, storage_path, content_type, size_bytes,
                                     checksum_sha256, visibility, folder, uploaded_by, created_at)
                values (:id, :applicationId, :originalFilename, :storagePath, :contentType, :sizeBytes,
                        :checksumSha256, :visibility, :folder, :uploadedBy, :createdAt)
                """)
                .param("id", file.id())
                .param("applicationId", file.applicationId())
                .param("originalFilename", file.originalFilename())
                .param("storagePath", file.storagePath())
                .param("contentType", file.contentType())
                .param("sizeBytes", file.sizeBytes())
                .param("checksumSha256", file.checksumSha256())
                .param("visibility", file.visibility().name())
                .param("folder", file.folder(), Types.VARCHAR)
                .param("uploadedBy", file.uploadedBy(), Types.VARCHAR)
                .param("createdAt", Timestamp.from(file.createdAt()))
                .update();
    }

    @Override
    public Optional<StoredFile> findActiveById(UUID id) {
        return jdbc.sql(SELECT + " where id = :id and deleted_at is null")
                .param("id", id)
                .query(JdbcFileRepository::mapRow)
                .optional();
    }

    @Override
    public List<StoredFile> findActiveByApp(UUID applicationId, String folder, int limit, long offset) {
        // O filtro de pasta é acrescentado só quando existe: o PostgreSQL não consegue
        // inferir o tipo de um parâmetro em ":folder is null".
        String sql = SELECT + " where application_id = :applicationId and deleted_at is null"
                + (folder != null ? " and folder = :folder" : "")
                + " order by created_at desc, id limit :limit offset :offset";
        JdbcClient.StatementSpec statement = jdbc.sql(sql)
                .param("applicationId", applicationId)
                .param("limit", limit)
                .param("offset", offset);
        if (folder != null) {
            statement = statement.param("folder", folder);
        }
        return statement.query(JdbcFileRepository::mapRow).list();
    }

    @Override
    public long countActiveByApp(UUID applicationId, String folder) {
        String sql = "select count(*) from fs_file where application_id = :applicationId and deleted_at is null"
                + (folder != null ? " and folder = :folder" : "");
        JdbcClient.StatementSpec statement = jdbc.sql(sql).param("applicationId", applicationId);
        if (folder != null) {
            statement = statement.param("folder", folder);
        }
        Long count = statement.query(Long.class).single();
        return count == null ? 0 : count;
    }

    @Override
    public long sumActiveSizeByApp(UUID applicationId) {
        Long total = jdbc.sql("""
                select cast(coalesce(sum(size_bytes), 0) as bigint)
                  from fs_file
                 where application_id = :applicationId
                   and deleted_at is null
                """)
                .param("applicationId", applicationId)
                .query(Long.class)
                .single();
        return total == null ? 0 : total;
    }

    @Override
    public boolean markDeleted(UUID id, Instant deletedAt) {
        int updated = jdbc.sql("update fs_file set deleted_at = :deletedAt where id = :id and deleted_at is null")
                .param("deletedAt", Timestamp.from(deletedAt))
                .param("id", id)
                .update();
        return updated > 0;
    }

    private static StoredFile mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        return new StoredFile(
                rs.getObject("id", UUID.class),
                rs.getObject("application_id", UUID.class),
                rs.getString("original_filename"),
                rs.getString("storage_path"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("checksum_sha256"),
                Visibility.valueOf(rs.getString("visibility")),
                rs.getString("folder"),
                rs.getString("uploaded_by"),
                rs.getTimestamp("created_at").toInstant(),
                deletedAt == null ? null : deletedAt.toInstant());
    }
}
