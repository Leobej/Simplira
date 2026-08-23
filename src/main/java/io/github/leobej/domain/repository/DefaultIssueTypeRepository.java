package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.issue.DefaultIssueType;

import java.util.List;

public interface DefaultIssueTypeRepository {
    List<DefaultIssueType> findAllOrdered();
}
