package dealership.order.infrastructure.persistence.adapter;

import dealership.order.core.application.port.out.UserRepository;
import dealership.order.core.domain.entity.user.User;
import dealership.order.core.domain.enums.UserRole;
import dealership.order.core.domain.exception.EntityNotFoundException;
import dealership.order.infrastructure.persistence.entity.UserJpaEntity;
import dealership.order.infrastructure.persistence.mapper.UserPersistenceMapper;
import dealership.order.infrastructure.persistence.repository.UserJpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class UserRepositoryAdapter implements UserRepository {
    private final UserJpaRepository jpaRepository;
    private final UserPersistenceMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository, UserPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = mapper.toJpa(user);
        return mapper.toDomain(jpaRepository.save(entity));
    }

    @Override
    public User findById(String id) {
        return jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).map(mapper::toDomain).orElseThrow(() -> new EntityNotFoundException("Пользователь с id '%s' не найден".formatted(id)));
    }

    @Override
    public List<User> findAll() {
        return jpaRepository.findByRemovedFalse().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return jpaRepository.findByRoleAndRemovedFalse(role).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        UserJpaEntity entity = jpaRepository.findByIdAndRemovedFalse(UUID.fromString(id)).orElseThrow(() -> new EntityNotFoundException("Пользователь с id '%s' не найден".formatted(id)));
        entity.setRemoved(true);
        jpaRepository.save(entity);
    }
}
