package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.DefaultIssueStatus;

import java.util.List;

public interface DefaultIssueStatusRepository {
    List<DefaultIssueStatus> findAllOrdered();
}
