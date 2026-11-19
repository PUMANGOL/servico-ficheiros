package ao.co.pumangol.fileservice.common;

import java.util.List;
import java.util.function.Function;

/** Página de resultados de uma listagem. */
public record PageResult<T>(List<T> items, int page, int size, long totalItems) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalItems / size);
    }

    public <R> PageResult<R> map(Function<? super T, ? extends R> mapper) {
        List<R> mapped = items.stream().<R>map(mapper).toList();
        return new PageResult<>(mapped, page, size, totalItems);
    }
}
