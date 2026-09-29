package com.platform.common.web;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list that grows without bound: {@code ?page=&size=} in, this shape out
 * (PLATFORM-EXPANSION.md §2). Pages are numbered from 0; {@code total} counts every matching row.
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, total);
    }

    /** A request's page and size, kept to what the server serves. */
    public record Request(int page, int size) {
        public Request {
            if (page < 0) {
                throw new IllegalArgumentException("page must be 0 or more");
            }
            if (size < 1 || size > MAX_SIZE) {
                throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
            }
        }

        public int offset() {
            return Math.multiplyExact(page, size);
        }
    }
}
