package ao.co.pumangol.fileservice.dashboard;

public record DashboardMetrics(
        long totalApplications,
        long activeApplications,
        long activeFiles,
        long storageBytes,
        long publicFiles,
        long privateFiles) {
}
