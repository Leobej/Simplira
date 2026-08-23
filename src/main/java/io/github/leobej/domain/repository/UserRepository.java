package io.github.leobej.domain.repository;

import io.github.leobej.domain.model.user.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    List<User> findAllById(Collection<UUID> ids);
    boolean existsByEmail(String email);
}