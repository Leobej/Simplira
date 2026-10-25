package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.shared.exception.EmailNotVerifiedException;

import java.util.UUID;

// §6: the actions that create or join a workspace need a verified address. Everything else -
// logging in, working in workspaces you are already in - is open to an unverified account.
//
// Switchable (simplira.auth.require-verified-email) so the test suites that are about other things
// need not verify every account they create; it is on everywhere else.
//
// Pure domain: no Spring annotations, no infrastructure imports.
// Wired as a bean by infrastructure.config.DomainServiceConfig.
public class VerifiedEmailGate {

    private final UserRepository userRepository;
    private final boolean enforced;

    public VerifiedEmailGate(UserRepository userRepository, boolean enforced) {
        this.userRepository = userRepository;
        this.enforced = enforced;
    }

    public void require(UUID userId) {
        if (enforced) {
            userRepository.findById(userId)
                    .filter(User::isEmailVerified)
                    .orElseThrow(EmailNotVerifiedException::new);
        }
    }

    public void require(User user) {
        if (enforced && !user.isEmailVerified()) {
            throw new EmailNotVerifiedException();
        }
    }
}
