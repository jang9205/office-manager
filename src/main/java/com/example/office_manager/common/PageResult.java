package com.example.office_manager.common;

import java.util.List;

public record PageResult<T>(List<T> content, long totalElements, int page, int size) {

    public int totalPages() {
        return totalElements == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }
}
