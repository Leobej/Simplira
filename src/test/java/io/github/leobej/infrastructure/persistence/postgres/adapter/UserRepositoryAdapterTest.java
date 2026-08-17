package io.github.leobej.infrastructure.persistence.postgres.adapter;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserRepositoryAdapterTest extends PostgresTestBase {

    @Autowired
    private UserRepository userRepository;

    @Test
    void savingAnExistingUserUpdatesItInsteadOfInserting() {
        User saved = userRepository.save(User.register("update-me@simplira.com", "hash", "Update Me"));

        saved.markEmailVerified();
        saved.changeName("Renamed");
        userRepository.save(saved);

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isEmailVerified()).isTrue();
        assertThat(reloaded.getFullName()).isEqualTo("Renamed");
        // Postgres keeps microseconds, the in-memory Instant keeps nanoseconds.
        assertThat(reloaded.getCreatedAt().truncatedTo(ChronoUnit.MILLIS))
                .isEqualTo(saved.getCreatedAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void aDuplicateEmailSurfacesAsEmailAlreadyExists() {
        userRepository.save(User.register("taken@simplira.com", "hash", "First"));

        // Skips the existsByEmail guard the way a concurrent registration would.
        User racingUser = User.register("taken@simplira.com", "hash", "Second");

        assertThatThrownBy(() -> userRepository.save(racingUser))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
