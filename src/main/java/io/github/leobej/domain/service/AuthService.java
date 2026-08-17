package io.github.leobej.domain.service;

import io.github.leobej.domain.model.user.User;
import io.github.leobej.domain.repository.UserRepository;
import io.github.leobej.infrastructure.security.JwtService;
import io.github.leobej.shared.exception.EmailAlreadyExistsException;
import io.github.leobej.shared.exception.InvalidCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public User register(String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        String hashed = passwordEncoder.encode(rawPassword);
        User user = User.register(email, hashed, fullName);
        return userRepository.save(user);
    }

    public LoginResult login(String email, String rawPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(user);
        return new LoginResult(user, accessToken, jwtService.getAccessTokenExpirySeconds());
    }

    public record LoginResult(User user, String accessToken, long expiresIn) {
    }
}
