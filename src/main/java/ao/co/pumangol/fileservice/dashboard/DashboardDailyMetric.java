package ao.co.pumangol.fileservice.dashboard;

import java.time.LocalDate;

public record DashboardDailyMetric(
        LocalDate day,
        long uploads,
        long downloads,
        long deletions,
        long applicationChanges) {
}
