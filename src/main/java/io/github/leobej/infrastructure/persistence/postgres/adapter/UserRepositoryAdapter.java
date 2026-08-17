package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.infrastructure.persistence.postgres.entity.UserEntity;
import io.github.leobej.infrastructure.persistence.postgres.jpa.JpaUserRepository;
import io.github.leobej.infrastructure.persistence.postgres.mapper.UserEntityMapper;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private static final String EMAIL_UNIQUE_CONSTRAINT = "uq_users_email";

    private final JpaUserRepository jpaRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        // The domain assigns the id, so the row has to be looked up to tell an insert from an update.
        boolean isNew = !jpaRepository.existsById(user.getId());
        UserEntity entity = UserEntityMapper.toEntity(user, isNew);
        try {
            // Flush inside this call so the unique violation surfaces here and not at commit time.
            return UserEntityMapper.toDomain(jpaRepository.saveAndFlush(entity));
        } catch (DataIntegrityViolationException ex) {
            if (isEmailAlreadyTaken(ex)) {
                throw new EmailAlreadyExistsException(user.getEmail());
            }
            throw ex;
        }
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(UserEntityMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email)
                .map(UserEntityMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    // Two concurrent registrations both pass the existsByEmail check; the unique index catches
    // the loser, and callers get the same conflict they would have got from the check.
    private static boolean isEmailAlreadyTaken(DataIntegrityViolationException ex) {
        if (ex.getCause() instanceof ConstraintViolationException cause && cause.getConstraintName() != null) {
            return cause.getConstraintName().equalsIgnoreCase(EMAIL_UNIQUE_CONSTRAINT);
        }
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(EMAIL_UNIQUE_CONSTRAINT);
    }
}
