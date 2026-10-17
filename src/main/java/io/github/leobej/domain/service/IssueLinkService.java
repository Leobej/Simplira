package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueLink;
import io.github.leobej.domain.model.issue.IssueLinkType;
import io.github.leobej.domain.model.issue.IssueLinkView;
import io.github.leobej.domain.repository.IssueLinkRepository;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.IssueLinkAlreadyExistsException;
import io.github.leobej.shared.exception.IssueLinkNotFoundException;
import io.github.leobej.shared.exception.IssueNotFoundException;
import io.github.leobej.shared.exception.ProjectNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class IssueLinkService {

    private final IssueLinkRepository issueLinkRepository;
    private final IssueRepository issueRepository;
    private final ProjectAuthorization projectAuthorization;
    private final BlockedFlagSync blockedFlagSync;
    private final Notifier notifier;

    public IssueLinkService(IssueLinkRepository issueLinkRepository,
                            IssueRepository issueRepository,
                            ProjectAuthorization projectAuthorization,
                            BlockedFlagSync blockedFlagSync,
                            Notifier notifier) {
        this.issueLinkRepository = issueLinkRepository;
        this.issueRepository = issueRepository;
        this.projectAuthorization = projectAuthorization;
        this.blockedFlagSync = blockedFlagSync;
        this.notifier = notifier;
    }

    // From both ends: an issue lists the links it made and the ones made to it.
    public List<IssueLinkView> list(UUID issueId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        List<IssueLink> links = issueLinkRepository.findInvolving(issueId);

        Map<UUID, Issue> linked = issueRepository
                .findAllById(links.stream().map(link -> link.otherEnd(issueId)).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));

        return links.stream()
                .filter(link -> linked.containsKey(link.otherEnd(issueId)))
                .map(link -> new IssueLinkView(link, linked.get(link.otherEnd(issueId))))
                .toList();
    }

    // The issue in the path is the source: POST /issues/A/links { B, BLOCKS } reads "A blocks B".
    public IssueLinkView create(UUID sourceIssueId, UUID targetIssueId, IssueLinkType type, UUID actorId) {
        Issue source = requireVisibleIssue(sourceIssueId, actorId);
        if (sourceIssueId.equals(targetIssueId)) {
            throw new BusinessRuleException("ISSUE_LINK_TO_SELF", "An issue cannot be linked to itself");
        }
        // Links stay inside a project. Across projects, someone could find their issue blocked by
        // one they cannot open, with no way to read why or remove it.
        Issue target = issueRepository.findById(targetIssueId)
                .filter(candidate -> candidate.getProjectId().equals(source.getProjectId()))
                .orElseThrow(() -> new IssueNotFoundException(targetIssueId));

        if (issueLinkRepository.existsBetween(sourceIssueId, targetIssueId, type)) {
            throw new IssueLinkAlreadyExistsException();
        }
        IssueLink link = issueLinkRepository.save(IssueLink.create(sourceIssueId, targetIssueId, type, actorId));
        // A link that blocks its target is told as the block, the thing that changes their day.
        if (link.blocks() && blockedFlagSync.refresh(targetIssueId)) {
            notifier.issueBlockedByLink(target, source, actorId);
        } else {
            notifier.issueLinked(source, target, type, actorId);
        }
        return new IssueLinkView(link, target);
    }

    // Through either end: a link belongs to both of its issues.
    public void delete(UUID issueId, UUID linkId, UUID actorId) {
        requireVisibleIssue(issueId, actorId);
        IssueLink link = issueLinkRepository.findById(linkId)
                .filter(candidate -> candidate.involves(issueId))
                .orElseThrow(() -> new IssueLinkNotFoundException(linkId));

        issueLinkRepository.delete(link);
        if (link.blocks()) {
            blockedFlagSync.refresh(link.getTargetIssueId());
        }
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
