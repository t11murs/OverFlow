package com.orderflow.catalog.application.port;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PageResult {
        content = List.copyOf(content);
    }
}

