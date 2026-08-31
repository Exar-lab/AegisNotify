package com.aegisnotify.user.application.dto;

import java.util.List;

/**
 * Generic page of results returned by {@code application.port.out}
 * adapters, mirroring {@code aegis-audit-service}'s {@code PagedResponse}
 * shape.
 *
 * @param content the page's items
 * @param page zero-based page number
 * @param size requested page size
 * @param totalElements total number of matching elements across all pages
 * @param totalPages total number of pages
 * @param <T> the element type
 */
public record PagedResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages) {
}
