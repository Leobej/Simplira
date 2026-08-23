package io.github.leobej.application.shared.response;

// The "meta" half of the envelope in §7, present only on paginated responses.
public record PageMeta(int page, int pageSize, long total) {
}
