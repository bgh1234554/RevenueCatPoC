package dartoo.revenuecatpoc.repository;

import dartoo.revenuecatpoc.domain.UserPlan;
import dartoo.revenuecatpoc.domain.enums.PlanDuration;
import dartoo.revenuecatpoc.domain.enums.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserPlanRepository extends JpaRepository<UserPlan,Long> {
    List<UserPlan> findAllByUser_IdOrderByStartAtDesc(Long id);

//    //현재 구독 이력 중 미래 포함 가장 최신 구독을 찾는다.
//    Optional<UserPlan> findTopByUserIdAndExpireAtAfterAndStatusInOrderByExpireAtDesc(Long userId, Instant now, List<PlanStatus> statuses);

    //startAt <= now < expireAt 조건으로 “현재 유효한 row 중 최신”을 찾기
    Optional<UserPlan> findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
            Long userId,
            Instant now1,
            Instant now2,
            List<PlanStatus> statuses
    );

    // currentExpireAt 이후(=미래 연장분) ACTIVE row들을 모음
    List<UserPlan> findAllByUser_IdAndStartAtGreaterThanEqualAndStatusOrderByStartAtAsc(
            Long userId,
            Instant startAt,
            PlanStatus status
    );

    //미래 연장분 플랜 존재 여부 확인하기
    boolean existsByUser_IdAndStartAtGreaterThanEqualAndStatus(Long id, Instant currentExpireAt, PlanStatus planStatus);

    //만료된 플랜 조회하기
    List<UserPlan> findAllByExpireAtBeforeAndStatusIn(Instant now, List<PlanStatus> active);

    @Query("""
            select up
            from UserPlan up
            join fetch up.user u
            where u.id in :userIds
              and up.startAt <= :now
              and up.expireAt > :now
              and up.status = :status
            """)
    //특정 사용자들의 활성 플랜 조회하기
    List<UserPlan> findAllActivePlansForUsers(@Param("userIds") List<Long> userIds,
                                              @Param("now") Instant now,
                                              @Param("status") PlanStatus status);

    //사용자가 특정 기간의 플랜을 가지고 있는지 확인하기
    boolean existsByUser_IdAndDuration(Long id, PlanDuration duration);

    //사용자가 특정 기간들 중 하나의 플랜을 가지고 있는지 확인하기
    boolean existsByUser_IdAndDurationIn(Long id, List<PlanDuration> durations);
}
