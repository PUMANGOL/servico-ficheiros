package ao.co.pumangol.fileservice.dashboard;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);
    private static final int RETENTION_DAYS = 90;

    private final JdbcClient jdbc;
    private final Clock clock;

    public AuditEventService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public void record(String eventType, String applicationCode, String resourceId, String details) {
        try {
            jdbc.sql("""
                    insert into fs_audit_event (id, event_type, application_code, resource_id, details, occurred_at)
                    values (:id, :eventType, :applicationCode, :resourceId, :details, :occurredAt)
                    """)
                    .param("id", UUID.randomUUID())
                    .param("eventType", eventType)
                    .param("applicationCode", applicationCode)
                    .param("resourceId", resourceId)
                    .param("details", details)
                    .param("occurredAt", Timestamp.from(clock.instant()))
                    .update();
        } catch (DataAccessException e) {
            log.error("Não foi possível registar o evento administrativo {} para a aplicação {}.",
                    eventType, applicationCode, e);
        }
    }

    public DashboardMetrics metrics() {
        return jdbc.sql("""
                select
                    (select count(*) from fs_application) as total_applications,
                    (select count(*) from fs_application where active = true) as active_applications,
                    (select count(*) from fs_file where deleted_at is null) as active_files,
                    (select cast(coalesce(sum(size_bytes), 0) as bigint)
                       from fs_file where deleted_at is null) as storage_bytes,
                    (select count(*) from fs_file where deleted_at is null and visibility = 'PUBLIC') as public_files,
                    (select count(*) from fs_file where deleted_at is null and visibility = 'PRIVATE') as private_files
                """)
                .query((rs, rowNum) -> new DashboardMetrics(
                        rs.getLong("total_applications"),
                        rs.getLong("active_applications"),
                        rs.getLong("active_files"),
                        rs.getLong("storage_bytes"),
                        rs.getLong("public_files"),
                        rs.getLong("private_files")))
                .single();
    }

    public List<DashboardActivity> recentActivity(int limit) {
        return jdbc.sql("""
                select id, event_type, application_code, resource_id, details, occurred_at
                  from fs_audit_event
                 order by occurred_at desc, id desc
                 limit :limit
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> new DashboardActivity(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getString("application_code"),
                        rs.getString("resource_id"),
                        rs.getString("details"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    public List<DashboardDailyMetric> dailyMetrics(int days) {
        Instant since = clock.instant().minus(days - 1L, ChronoUnit.DAYS)
                .truncatedTo(ChronoUnit.DAYS);
        return jdbc.sql("""
                select cast(occurred_at as date) as event_day,
                       sum(case when event_type = 'FILE_UPLOADED' then 1 else 0 end) as uploads,
                       sum(case when event_type = 'FILE_DOWNLOADED' then 1 else 0 end) as downloads,
                       sum(case when event_type = 'FILE_DELETED' then 1 else 0 end) as deletions,
                       sum(case when event_type in ('APP_CREATED', 'APP_UPDATED', 'API_KEY_ROTATED')
                                then 1 else 0 end) as application_changes
                  from fs_audit_event
                 where occurred_at >= :since
                 group by cast(occurred_at as date)
                 order by event_day
                """)
                .param("since", Timestamp.from(since))
                .query((rs, rowNum) -> new DashboardDailyMetric(
                        rs.getDate("event_day").toLocalDate(),
                        rs.getLong("uploads"),
                        rs.getLong("downloads"),
                        rs.getLong("deletions"),
                        rs.getLong("application_changes")))
                .list();
    }

    @Scheduled(cron = "0 15 3 * * *")
    public void deleteExpiredEvents() {
        try {
            int deleted = jdbc.sql("delete from fs_audit_event where occurred_at < :cutoff")
                    .param("cutoff", Timestamp.from(clock.instant().minus(RETENTION_DAYS, ChronoUnit.DAYS)))
                    .update();
            if (deleted > 0) {
                log.info("Removidos {} eventos de auditoria com mais de {} dias.", deleted, RETENTION_DAYS);
            }
        } catch (DataAccessException e) {
            log.error("Não foi possível aplicar a retenção dos eventos de auditoria.", e);
        }
    }
}
