package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.IssueStatus;
import io.github.leobej.domain.model.issue.IssueType;
import io.github.leobej.domain.repository.DefaultIssueStatusRepository;
import io.github.leobej.domain.repository.DefaultIssueTypeRepository;
import io.github.leobej.domain.repository.IssueStatusRepository;
import io.github.leobej.domain.repository.IssueTypeRepository;

import java.util.UUID;

// A project with no issue types and no statuses cannot hold a single issue, so every project is
// seeded from the template tables the moment it is created (§31, §33).
public class ProjectSetupService {

    private final IssueTypeRepository issueTypeRepository;
    private final IssueStatusRepository issueStatusRepository;
    private final DefaultIssueTypeRepository defaultIssueTypeRepository;
    private final DefaultIssueStatusRepository defaultIssueStatusRepository;

    public ProjectSetupService(IssueTypeRepository issueTypeRepository,
                               IssueStatusRepository issueStatusRepository,
                               DefaultIssueTypeRepository defaultIssueTypeRepository,
                               DefaultIssueStatusRepository defaultIssueStatusRepository) {
        this.issueTypeRepository = issueTypeRepository;
        this.issueStatusRepository = issueStatusRepository;
        this.defaultIssueTypeRepository = defaultIssueTypeRepository;
        this.defaultIssueStatusRepository = defaultIssueStatusRepository;
    }

    // Copies rather than references the templates: editing a default later changes what future
    // projects get, never what an existing project already has.
    public void seedDefaults(UUID projectId) {
        defaultIssueTypeRepository.findAllOrdered()
                .forEach(template -> issueTypeRepository.save(IssueType.from(projectId, template)));

        defaultIssueStatusRepository.findAllOrdered()
                .forEach(template -> issueStatusRepository.save(IssueStatus.from(projectId, template)));
    }
}
