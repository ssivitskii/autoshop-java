package dealership.storage.infrastructure.persistence.repository;

import dealership.storage.infrastructure.persistence.entity.AssemblyOrderJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssemblyOrderJpaRepository extends JpaRepository<AssemblyOrderJpaEntity, UUID> {
    Optional<AssemblyOrderJpaEntity> findByIdAndRemovedFalse(UUID id);

    boolean existsBySourceOrderIdAndRemovedFalse(String sourceOrderId);

    Optional<AssemblyOrderJpaEntity> findBySourceOrderIdAndRemovedFalse(String sourceOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AssemblyOrderJpaEntity a where a.sourceOrderId = :sourceOrderId")
    Optional<AssemblyOrderJpaEntity> findBySourceOrderIdForUpdate(@Param("sourceOrderId") String sourceOrderId);
}
