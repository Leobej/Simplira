package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.issue.Issue;
import io.github.leobej.domain.model.issue.IssueFilter;
import io.github.leobej.domain.model.issue.IssuePage;
import io.github.leobej.domain.repository.IssueRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaIssueRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.IssueEntityMapper;
import io.github.leobej.infrastructure.persistence.postgres.entity.IssueAssigneeEntity;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class IssueRepositoryAdapter implements IssueRepository {

    private final JpaIssueRepository jpaRepository;

    public IssueRepositoryAdapter(JpaIssueRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Issue save(Issue issue) {
        boolean isNew = !jpaRepository.existsById(issue.getId());
        IssueEntity entity = IssueEntityMapper.toEntity(issue, isNew);
        return IssueEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
    }

    @Override
    public Optional<Issue> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(IssueEntityMapper::toDomain);
    }

    @Override
    public Optional<Issue> findByNumber(UUID projectId, int number) {
        return jpaRepository.findByProjectIdAndNumberAndDeletedAtIsNull(projectId, number)
                .map(IssueEntityMapper::toDomain);
    }

    // §34: only the predicates that are actually present get composed.
    @Override
    public IssuePage search(IssueFilter filter) {
        Specification<IssueEntity> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.isNull(root.get("deletedAt")));
            predicates.add(builder.equal(root.get("projectId"), filter.projectId()));

            if (filter.sprintId() != null) {
                predicates.add(builder.equal(root.get("sprintId"), filter.sprintId()));
            }
            if (filter.backlogOnly()) {
                predicates.add(builder.isNull(root.get("sprintId")));
            }
            if (filter.topLevelOnly()) {
                predicates.add(builder.isNull(root.get("parentId")));
            }
            if (filter.statusIds() != null && !filter.statusIds().isEmpty()) {
                predicates.add(root.get("statusId").in(filter.statusIds()));
            }
            if (filter.typeId() != null) {
                predicates.add(builder.equal(root.get("typeId"), filter.typeId()));
            }
            if (filter.priority() != null) {
                predicates.add(builder.equal(root.get("priority"), filter.priority()));
            }
            if (filter.reporterId() != null) {
                predicates.add(builder.equal(root.get("reporterId"), filter.reporterId()));
            }
            if (filter.assigneeId() != null) {
                Subquery<UUID> assigned = query.subquery(UUID.class);
                Root<IssueAssigneeEntity> assignee = assigned.from(IssueAssigneeEntity.class);
                assigned.select(assignee.get("issueId"))
                        .where(builder.equal(assignee.get("userId"), filter.assigneeId()));
                predicates.add(root.get("id").in(assigned));
            }
            if (filter.unassignedOnly()) {
                Subquery<UUID> anyone = query.subquery(UUID.class);
                Root<IssueAssigneeEntity> assignee = anyone.from(IssueAssigneeEntity.class);
                anyone.select(assignee.get("issueId"))
                        .where(builder.equal(assignee.get("issueId"), root.get("id")));
                predicates.add(builder.not(builder.exists(anyone)));
            }
            if (filter.blocked() != null) {
                predicates.add(builder.equal(root.get("blocked"), filter.blocked()));
            }
            if (filter.dueBefore() != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("dueDate"), filter.dueBefore()));
            }
            if (filter.dueAfter() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("dueDate"), filter.dueAfter()));
            }
            if (filter.text() != null && !filter.text().isBlank()) {
                String pattern = "%" + filter.text().toLowerCase(Locale.ROOT) + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("title")), pattern),
                        builder.like(builder.lower(root.get("description")), pattern)));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };

        // Board order first, then the number, so an issue with no position still lands somewhere
        // predictable rather than wherever the database felt like.
        Sort sort = filter.sprintId() != null
                ? Sort.by(Sort.Order.asc("sprintPosition").nullsLast(), Sort.Order.asc("number"))
                : Sort.by(Sort.Order.asc("backlogPosition").nullsLast(), Sort.Order.asc("number"));

        Page<IssueEntity> page = jpaRepository.findAll(specification,
                PageRequest.of(filter.page(), filter.pageSize(), sort));

        return new IssuePage(
                page.getContent().stream().map(IssueEntityMapper::toDomain).toList(),
                page.getTotalElements(),
                filter.page(),
                filter.pageSize());
    }

    @Override
    public List<Issue> findSubtasks(UUID parentId) {
        return jpaRepository.findByParentIdAndDeletedAtIsNullOrderByNumber(parentId).stream()
                .map(IssueEntityMapper::toDomain)
                .toList();
    }

    @Override
    public List<Issue> findBySprintId(UUID sprintId) {
        return jpaRepository.findBySprintIdAndDeletedAtIsNull(sprintId).stream()
                .map(IssueEntityMapper::toDomain)
                .toList();
    }

    @Override
    public int nextNumber(UUID projectId) {
        return jpaRepository.maxNumber(projectId) + 1;
    }

    @Override
    public double maxBacklogPosition(UUID projectId) {
        return jpaRepository.maxBacklogPosition(projectId);
    }

    @Override
    public double maxSprintPosition(UUID sprintId) {
        return jpaRepository.maxSprintPosition(sprintId);
    }

    @Override
    public long countByStatusId(UUID statusId) {
        return jpaRepository.countByStatusIdAndDeletedAtIsNull(statusId);
    }

    @Override
    public long countByTypeId(UUID typeId) {
        return jpaRepository.countByTypeIdAndDeletedAtIsNull(typeId);
    }

    @Override
    @Transactional
    public void migrateStatus(UUID fromStatusId, UUID toStatusId) {
        jpaRepository.migrateStatus(fromStatusId, toStatusId);
    }
}
