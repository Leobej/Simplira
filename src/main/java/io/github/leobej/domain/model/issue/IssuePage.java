package io.github.leobej.domain.model.issue;

import java.util.List;

public record IssuePage(List<Issue> items, long total, int page, int pageSize) {
}
