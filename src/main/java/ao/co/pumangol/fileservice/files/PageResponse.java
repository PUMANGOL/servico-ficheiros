package ao.co.pumangol.fileservice.files;

import ao.co.pumangol.fileservice.common.PageResult;
import java.util.List;

/** Página de uma listagem, tal como é devolvida em JSON. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> PageResponse<T> from(PageResult<T> result) {
        return new PageResponse<>(result.items(), result.page(), result.size(), result.totalItems(),
                result.totalPages());
    }
}
