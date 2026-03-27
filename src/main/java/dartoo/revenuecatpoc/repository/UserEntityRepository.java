package dartoo.revenuecatpoc.repository;

import dartoo.revenuecatpoc.domain.UserEntity;
import dartoo.revenuecatpoc.domain.enums.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUserEmail(String userEmail);

    boolean existsByUserEmail(String userEmail);

    void deleteByUserEmail(String userEmail);

    List<UserEntity> findAllByPlanAndPlanExpireAtBefore(PlanType planType, Instant now);
}
