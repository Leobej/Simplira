package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.label.Label;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.LabelRepository;
import io.github.leobej.shared.exception.DuplicateNameException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.LabelNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class LabelService {

    private final LabelRepository labelRepository;
    private final IssueRepository issueRepository;
    private final ProjectAuthorization projectAuthorization;

    public LabelService(LabelRepository labelRepository,
                        IssueRepository issueRepository,
                        ProjectAuthorization projectAuthorization) {
        this.labelRepository = labelRepository;
        this.issueRepository = issueRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public List<Label> list(UUID projectId, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);
        return labelRepository.findByProjectId(projectId);
    }

    // Defining the project's vocabulary is a team leader's job (§7); using it is everyone's.
    public Label create(UUID projectId, String name, String color, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        if (labelRepository.existsByName(projectId, name)) {
            throw new DuplicateNameException(name);
        }
        return labelRepository.save(Label.create(projectId, name, color, actorId));
    }

    public Label update(UUID projectId, UUID labelId, String name, String color, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        Label label = loadInProject(projectId, labelId);

        if (name != null && !name.isBlank() && !name.equals(label.getName())) {
            if (labelRepository.existsByName(projectId, name)) {
                throw new DuplicateNameException(name);
            }
            label.rename(name);
        }
        if (color != null && !color.isBlank()) {
            label.recolour(color);
        }
        return labelRepository.save(label);
    }

    // The label itself is soft-deleted for the record, but it comes off every issue: leaving it
    // attached would show issues wearing a label nobody can see or select any more.
    public void delete(UUID projectId, UUID labelId, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        Label label = loadInProject(projectId, labelId);

        labelRepository.detachEverywhere(labelId);
        label.delete(actorId);
        labelRepository.save(label);
    }

    public List<Label> labelsOf(UUID issueId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        return labelRepository.findAllById(labelRepository.labelIdsOf(issueId));
    }

    // Idempotent, like assignees: a repeat attaches nothing new. Returns the issue's labels so a
    // client can re-render from one response.
    public List<Label> attach(UUID issueId, UUID labelId, UUID actorId) {
        Issue issue = requireVisibleIssue(issueId, actorId);
        // A label from another project is not one this issue can wear.
        loadInProject(issue.getProjectId(), labelId);

        labelRepository.attach(issueId, labelId);
        return labelRepository.findAllById(labelRepository.labelIdsOf(issueId));
    }

    public void detach(UUID issueId, UUID labelId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        labelRepository.detach(issueId, labelId);
    }

    // For rendering cards: one query for a whole page of issues.
    public Map<UUID, List<UUID>> labelIdsOf(Collection<UUID> issueIds) {
        return labelRepository.labelIdsOf(issueIds);
    }

    private Label loadInProject(UUID projectId, UUID labelId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new LabelNotFoundException(labelId));
        if (!label.getProjectId().equals(projectId)) {
            throw new LabelNotFoundException(labelId);
        }
        return label;
    }

    private Issue requireVisibleIssue(UUID issueId, UUID actorId) {
        Issue issue = issueRepository.findById(issueId)
                .orElseThrow(() -> new IssueNotFoundException(issueId));
        try {
            projectAuthorization.requireAccess(issue.getProjectId(), actorId);
        } catch (ProjectNotFoundException ex) {
            throw new IssueNotFoundException(issueId);
        }
        return issue;
    }
}
