package io.github.leobej.domain.model.audit;

import java.util.List;

public record AuditPage(List<AuditEntry> items, long total, int page, int pageSize) {
}
