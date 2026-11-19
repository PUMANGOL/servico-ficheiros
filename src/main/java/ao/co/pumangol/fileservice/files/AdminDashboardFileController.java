package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.dashboard.DashboardFilePage;
import ao.co.pumangol.fileservice.dashboard.DashboardFileService;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard/files")
public class AdminDashboardFileController {

    private final DashboardFileService dashboardFiles;
    private final FileService fileService;

    public AdminDashboardFileController(DashboardFileService dashboardFiles, FileService fileService) {
        this.dashboardFiles = dashboardFiles;
        this.fileService = fileService;
    }

    @GetMapping
    public DashboardFilePage list(
            @RequestParam(name = "applicationCode", required = false) String applicationCode,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "visibility", required = false) String visibility,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return dashboardFiles.list(applicationCode, query, visibility, page, size);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(
            @PathVariable("id") UUID id,
            @RequestParam(name = "download", defaultValue = "false") boolean download) {
        return FileDownloads.respond(fileService.openForAdmin(id), download, CacheControl.noCache().cachePrivate());
    }
}
