package com.simplira.simplira.infrastructure.persistence.postgres.adapter;

import com.simplira.simplira.domain.model.user.User;
import com.simplira.simplira.domain.repository.UserRepository;
import com.simplira.simplira.infrastructure.persistence.postgres.entity.UserEntity;
import com.simplira.simplira.infrastructure.persistence.postgres.jpa.JpaUserRepository;
import com.simplira.simplira.infrastructure.persistence.postgres.mapper.UserEntityMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserEntity entity = UserEntityMapper.toEntity(user);
        UserEntity saved = jpaRepository.save(entity);
        return UserEntityMapper.toDomain(saved);
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
}
