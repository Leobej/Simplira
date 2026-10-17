package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.comment.Comment;
import io.github.leobej.domain.repository.CommentRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.CommentEntity;
import io.github.leobej.infrastructure.persistence.postgres.entity.CommentMentionEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaCommentMentionRepository;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaCommentRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.CommentEntityMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class CommentRepositoryAdapter implements CommentRepository {

    private final JpaCommentRepository commentRepository;
    private final JpaCommentMentionRepository mentionRepository;

    public CommentRepositoryAdapter(JpaCommentRepository commentRepository,
                                    JpaCommentMentionRepository mentionRepository) {
        this.commentRepository = commentRepository;
        this.mentionRepository = mentionRepository;
    }

    // One transaction for the comment and its mentions: they are a single aggregate, and a
    // comment whose text says @Jane while the mention rows disagree would be worse than either.
    @Override
    @Transactional
    public Comment save(Comment comment) {
        boolean isNew = !commentRepository.existsById(comment.getId());
        CommentEntity saved = commentRepository.saveAndFlush(CommentEntityMapper.toEntity(comment, isNew));
        syncMentions(comment.getId(), comment.getMentionedUserIds());
        return CommentEntityMapper.toDomain(saved, mentionsOf(comment.getId()));
    }

    @Override
    public Optional<Comment> findById(UUID id) {
        return commentRepository.findByIdAndDeletedAtIsNull(id)
                .map(entity -> CommentEntityMapper.toDomain(entity, mentionsOf(entity.getId())));
    }

    @Override
    public List<Comment> findByIssueId(UUID issueId) {
        List<CommentEntity> comments = commentRepository.findByIssueIdAndDeletedAtIsNullOrderByCreatedAt(issueId);
        if (comments.isEmpty()) {
            return List.of();
        }
        // Every comment's mentions in one query rather than one per comment.
        Map<UUID, Set<UUID>> mentions = mentionRepository
                .findByCommentIdIn(comments.stream().map(CommentEntity::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        CommentMentionEntity::getCommentId,
                        Collectors.mapping(CommentMentionEntity::getMentionedUserId,
                                Collectors.toCollection(LinkedHashSet::new))));

        return comments.stream()
                .map(entity -> CommentEntityMapper.toDomain(entity,
                        mentions.getOrDefault(entity.getId(), new LinkedHashSet<>())))
                .toList();
    }

    // Brings the rows in line with the comment's text: mentions dropped in an edit are deleted,
    // new ones inserted, and unchanged ones keep their original created_at.
    private void syncMentions(UUID commentId, Set<UUID> wanted) {
        List<CommentMentionEntity> existing = mentionRepository.findByCommentId(commentId);
        Set<UUID> present = existing.stream()
                .map(CommentMentionEntity::getMentionedUserId)
                .collect(Collectors.toSet());

        existing.stream()
                .filter(mention -> !wanted.contains(mention.getMentionedUserId()))
                .forEach(mentionRepository::delete);
        wanted.stream()
                .filter(userId -> !present.contains(userId))
                .forEach(userId -> mentionRepository.save(CommentMentionEntity.of(commentId, userId)));
    }

    private Set<UUID> mentionsOf(UUID commentId) {
        return mentionRepository.findByCommentId(commentId).stream()
                .map(CommentMentionEntity::getMentionedUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
