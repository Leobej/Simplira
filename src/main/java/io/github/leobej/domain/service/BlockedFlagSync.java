package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.IssueLink;
import io.github.leobej.domain.repository.IssueLinkRepository;
import io.github.leobej.domain.repository.IssueRepository;

import java.util.UUID;

// Keeps an issue's blocked flag in step with the BLOCKS links pointing at it (§4). The flag is
// stored rather than derived so the isBlocked filter (§34) stays a column check, which makes this
// the one place that writes its link-driven half.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class BlockedFlagSync {

    private final IssueRepository issueRepository;
    private final IssueLinkRepository issueLinkRepository;

    public BlockedFlagSync(IssueRepository issueRepository, IssueLinkRepository issueLinkRepository) {
        this.issueRepository = issueRepository;
        this.issueLinkRepository = issueLinkRepository;
    }

    public boolean blockedByLinks(UUID issueId) {
        return issueLinkRepository.isBlockedByLinks(issueId);
    }

    // Saves only when the flag moves: a second blocker changes nothing, and bumping the version
    // regardless would 409 whoever is mid-edit on the issue for no visible reason. Returns whether
    // it moved, which is what decides if anyone is told.
    public boolean refresh(UUID issueId) {
        return issueRepository.findById(issueId)
                .filter(issue -> issue.applyLinkBlocking(blockedByLinks(issueId)))
                .map(issueRepository::save)
                .isPresent();
    }

    // A deleted issue blocks nothing: its links go, and whatever it was blocking is re-checked.
    public void issueDeleted(UUID issueId) {
        issueLinkRepository.deleteInvolving(issueId).stream()
                .filter(link -> link.blocks() && link.getSourceIssueId().equals(issueId))
                .map(IssueLink::getTargetIssueId)
                .distinct()
                .forEach(this::refresh);
    }
}
