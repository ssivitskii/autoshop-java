package dealership.order.infrastructure.persistence.mapper;

import dealership.order.core.domain.entity.user.User;
import dealership.order.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserPersistenceMapper {
    public User toDomain(UserJpaEntity entity) {
        return new User(entity.getId().toString(), entity.getUsername(), entity.getPassword(), entity.getFullName(), entity.getEmail(), entity.getPhone(), entity.getRole());
    }

    public UserJpaEntity toJpa(User domain) {
        UserJpaEntity entity = new UserJpaEntity();
        if (domain.getId() != null) {
            entity.setId(UUID.fromString(domain.getId()));
        }
        entity.setUsername(domain.getUsername());
        entity.setPassword(domain.getPassword());
        entity.setFullName(domain.getFullName());
        entity.setEmail(domain.getEmail());
        entity.setPhone(domain.getPhone());
        entity.setRole(domain.getRole());
        return entity;
    }
}
