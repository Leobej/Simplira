package com.simplira.simplira.domain.service;

import com.simplira.simplira.domain.model.user.User;
import com.simplira.simplira.domain.repository.UserRepository;
import com.simplira.simplira.shared.exception.EmailAlreadyExistsException;
import com.simplira.simplira.domain.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String email, String rawPassword, String fullName) {
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        String hashed = passwordEncoder.encode(rawPassword);
        User user = User.register(email, hashed, fullName);
        return userRepository.save(user);
    }
}
