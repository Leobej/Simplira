package io.github.leobej.domain.model.audit;

import io.github.leobej.domain.model.user.User;

// An entry with its actor loaded, so a history reads "Jane moved it" without a lookup per line.
public record AuditEntryView(AuditEntry entry, User actor) {}
