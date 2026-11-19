package ao.co.pumangol.fileservice.dashboard;

import ao.co.pumangol.fileservice.config.FileServiceProperties;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private static final Logger log = LoggerFactory.getLogger(AdminDashboardController.class);

    private final AuditEventService events;
    private final Clock clock;
    private final JdbcClient jdbc;
    private final Path storagePath;

    public AdminDashboardController(
            AuditEventService events, Clock clock, JdbcClient jdbc, FileServiceProperties properties) {
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
        this.storagePath = Path.of(properties.storage().basePath());
    }

    @GetMapping("/health")
    public DashboardHealth health() {
        String databaseStatus = "UP";
        try {
            jdbc.sql("select 1").query(Integer.class).single();
        } catch (DataAccessException e) {
            databaseStatus = "DOWN";
            log.warn("Verificação de saúde: a base de dados não está disponível.", e);
        }
        String storageStatus = Files.isDirectory(storagePath) && Files.isWritable(storagePath) ? "UP" : "DOWN";
        String status = databaseStatus.equals("UP") && storageStatus.equals("UP") ? "UP" : "DOWN";
        return new DashboardHealth(
                status,
                clock.instant(),
                ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
                databaseStatus,
                storageStatus);
    }

    @GetMapping("/metrics")
    public DashboardMetrics metrics() {
        return events.metrics();
    }

    @GetMapping("/activity")
    public List<DashboardActivity> activity(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        if (limit < 1 || limit > 200) {
            throw ao.co.pumangol.fileservice.common.ApiException.badRequest(
                    "INVALID_LIMIT", "O parâmetro 'limit' deve estar entre 1 e 200.");
        }
        return events.recentActivity(limit);
    }

    @GetMapping("/daily")
    public List<DashboardDailyMetric> daily(
            @RequestParam(name = "days", defaultValue = "14") int days) {
        if (days < 1 || days > 90) {
            throw ao.co.pumangol.fileservice.common.ApiException.badRequest(
                    "INVALID_RANGE", "O parâmetro 'days' deve estar entre 1 e 90.");
        }
        return events.dailyMetrics(days);
    }
}
