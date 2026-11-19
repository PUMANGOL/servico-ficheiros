package ao.co.pumangol.fileservice.dashboard;

import ao.co.pumangol.fileservice.common.ApiException;
import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class DashboardFileService {

    private final JdbcClient jdbc;

    public DashboardFileService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public DashboardFilePage list(String applicationCode, String query, String visibility, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.badRequest("INVALID_PAGINATION",
                    "Paginação inválida: page >= 0 e size entre 1 e 100.");
        }

        StringBuilder where = new StringBuilder(" where f.deleted_at is null");
        JdbcClient.StatementSpec countStatement;
        JdbcClient.StatementSpec selectStatement;

        if (applicationCode != null && !applicationCode.isBlank()) {
            where.append(" and a.code = :applicationCode");
        }
        if (visibility != null && !visibility.isBlank()) {
            String normalizedVisibility = visibility.trim().toUpperCase(java.util.Locale.ROOT);
            if (!normalizedVisibility.equals("PUBLIC") && !normalizedVisibility.equals("PRIVATE")) {
                throw ApiException.badRequest("INVALID_VISIBILITY", "A visibilidade deve ser PUBLIC ou PRIVATE.");
            }
            where.append(" and f.visibility = :visibility");
        } else {
            visibility = null;
        }
        if (query != null && !query.isBlank()) {
            where.append("""
                     and (lower(f.original_filename) like lower(:query)
                          or lower(a.code) like lower(:query)
                          or lower(a.name) like lower(:query)
                          or lower(coalesce(f.folder, '')) like lower(:query)
                          or cast(f.id as varchar) like :query)
                    """);
        } else {
            query = null;
        }

        String from = " from fs_file f join fs_application a on a.id = f.application_id" + where;
        countStatement = jdbc.sql("select count(*)" + from);
        selectStatement = jdbc.sql("""
                select f.id, a.code as application_code, a.name as application_name,
                       f.original_filename, f.content_type, f.size_bytes, f.checksum_sha256,
                       f.visibility, f.folder, f.uploaded_by, f.created_at
                """ + from + " order by f.created_at desc, f.id limit :limit offset :offset");

        if (applicationCode != null && !applicationCode.isBlank()) {
            countStatement = countStatement.param("applicationCode", applicationCode.trim());
            selectStatement = selectStatement.param("applicationCode", applicationCode.trim());
        }
        if (visibility != null) {
            String normalizedVisibility = visibility.trim().toUpperCase(java.util.Locale.ROOT);
            countStatement = countStatement.param("visibility", normalizedVisibility);
            selectStatement = selectStatement.param("visibility", normalizedVisibility);
        }
        if (query != null) {
            String pattern = "%" + query.trim() + "%";
            countStatement = countStatement.param("query", pattern);
            selectStatement = selectStatement.param("query", pattern);
        }

        long total = countStatement.query(Long.class).single();
        List<DashboardFile> files = selectStatement
                .param("limit", size)
                .param("offset", (long) page * size)
                .query((rs, rowNum) -> new DashboardFile(
                        rs.getObject("id", java.util.UUID.class),
                        rs.getString("application_code"),
                        rs.getString("application_name"),
                        rs.getString("original_filename"),
                        rs.getString("content_type"),
                        rs.getLong("size_bytes"),
                        rs.getString("checksum_sha256"),
                        rs.getString("visibility"),
                        rs.getString("folder"),
                        rs.getString("uploaded_by"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();

        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return new DashboardFilePage(files, page, size, total, totalPages);
    }
}
