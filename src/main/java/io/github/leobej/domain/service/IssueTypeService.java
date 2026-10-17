package io.github.leobej.domain.service;

import io.github.leobej.domain.model.issue.IssueType;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.domain.repository.IssueTypeRepository;
import io.github.leobej.shared.exception.BusinessRuleException;
import io.github.leobej.shared.exception.DuplicateNameException;
import io.github.leobej.shared.exception.IssueTypeNotFoundException;

import java.util.List;
import java.util.UUID;

// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class IssueTypeService {

    private final IssueTypeRepository issueTypeRepository;
    private final IssueRepository issueRepository;
    private final ProjectAuthorization projectAuthorization;

    public IssueTypeService(IssueTypeRepository issueTypeRepository,
                            IssueRepository issueRepository,
                            ProjectAuthorization projectAuthorization) {
        this.issueTypeRepository = issueTypeRepository;
        this.issueRepository = issueRepository;
        this.projectAuthorization = projectAuthorization;
    }

    public List<IssueType> list(UUID projectId, UUID actorId) {
        projectAuthorization.requireAccess(projectId, actorId);
        return issueTypeRepository.findByProjectId(projectId);
    }

    public IssueType create(UUID projectId, String name, String icon, String color,
                            boolean makeDefault, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);

        if (issueTypeRepository.existsByName(projectId, name)) {
            throw new DuplicateNameException(name);
        }
        List<IssueType> existing = issueTypeRepository.findByProjectId(projectId);
        int nextOrder = existing.stream().mapToInt(IssueType::getSortOrder).max().orElse(0) + 1;

        IssueType created = IssueType.create(projectId, name, icon, color, false, nextOrder);
        IssueType saved = issueTypeRepository.save(created);

        if (makeDefault) {
            promoteToDefault(saved, existing);
        }
        return issueTypeRepository.findById(saved.getId()).orElse(saved);
    }

    public IssueType update(UUID projectId, UUID typeId, String name, String icon, String color,
                            Boolean makeDefault, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        IssueType type = loadInProject(projectId, typeId);

        if (name != null && !name.isBlank() && !name.equals(type.getName())) {
            if (issueTypeRepository.existsByName(projectId, name)) {
                throw new DuplicateNameException(name);
            }
            type.rename(name);
        }
        type.restyle(icon, color);

        if (Boolean.TRUE.equals(makeDefault) && !type.isDefault()) {
            promoteToDefault(type, issueTypeRepository.findByProjectId(projectId));
        } else if (Boolean.FALSE.equals(makeDefault) && type.isDefault()) {
            // Refused rather than silently obeyed: something has to be the default, otherwise a
            // new issue has no type to fall back on.
            throw new BusinessRuleException("DEFAULT_ISSUE_TYPE_REQUIRED",
                    "Make another type the default instead of clearing this one");
        }
        return issueTypeRepository.save(type);
    }

    public void delete(UUID projectId, UUID typeId, UUID actorId) {
        projectAuthorization.requireManage(projectId, actorId);
        IssueType type = loadInProject(projectId, typeId);

        List<IssueType> remaining = issueTypeRepository.findByProjectId(projectId);
        if (remaining.size() <= 1) {
            throw new BusinessRuleException("LAST_ISSUE_TYPE",
                    "A project must keep at least one issue type");
        }
        if (type.isDefault()) {
            throw new BusinessRuleException("DEFAULT_ISSUE_TYPE_REQUIRED",
                    "Make another type the default before deleting this one");
        }
        // Unlike statuses, §33 defines no migration for types, so a type in use is simply kept:
        // retyping issues is a decision for whoever is reorganising the project.
        long inUse = issueRepository.countByTypeId(typeId);
        if (inUse > 0) {
            throw new BusinessRuleException("ISSUE_TYPE_IN_USE",
                    "This type is used by " + inUse + " issues; retype them first");
        }
        type.delete(actorId);
        issueTypeRepository.save(type);
    }

    // The unique index allows one default per project, so the old one is cleared first.
    private void promoteToDefault(IssueType type, List<IssueType> existing) {
        existing.stream()
                .filter(IssueType::isDefault)
                .filter(other -> !other.getId().equals(type.getId()))
                .forEach(other -> {
                    other.clearDefault();
                    issueTypeRepository.save(other);
                });
        type.makeDefault();
        issueTypeRepository.save(type);
    }

    private IssueType loadInProject(UUID projectId, UUID typeId) {
        IssueType type = issueTypeRepository.findById(typeId)
                .orElseThrow(() -> new IssueTypeNotFoundException(typeId));
        // Scoped deliberately: a type belonging to another project must not be reachable here.
        if (!type.getProjectId().equals(projectId)) {
            throw new IssueTypeNotFoundException(typeId);
        }
        return type;
    }
}
