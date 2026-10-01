package dealership.order.infrastructure.persistence.repository;

import dealership.order.core.domain.enums.UserRole;
import dealership.order.infrastructure.persistence.entity.UserJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID> {
    List<UserJpaEntity> findByRemovedFalse();

    Optional<UserJpaEntity> findByIdAndRemovedFalse(UUID id);

    List<UserJpaEntity> findByRoleAndRemovedFalse(UserRole role);
}
