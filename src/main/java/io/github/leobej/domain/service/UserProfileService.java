package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.InvalidCredentialsException;

import java.util.UUID;

// The signed-in user's own profile. Not in the audit log: that is workspace history, and a
// profile belongs to no workspace.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class UserProfileService {

    private final UserRepository userRepository;

    public UserProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Mentions keep working after a rename: the token carries the id, and the name is display only.
    public User rename(UUID userId, String fullName) {
        User user = load(userId);
        user.changeName(fullName.strip());
        return userRepository.save(user);
    }

    private User load(UUID userId) {
        return userRepository.findById(userId).orElseThrow(InvalidCredentialsException::new);
    }
}
