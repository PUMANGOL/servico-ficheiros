package ao.co.pumangol.fileservice.dashboard;

import java.util.List;

public record DashboardFilePage(
        List<DashboardFile> items,
        int page,
        int size,
        long totalItems,
        int totalPages) {

    public DashboardFilePage {
        items = List.copyOf(items);
    }
}
