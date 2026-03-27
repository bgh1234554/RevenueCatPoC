package dartoo.revenuecatpoc.domain;

import dartoo.revenuecatpoc.domain.enums.PlanStatus;
import dartoo.revenuecatpoc.domain.enums.PlanType;
import dartoo.revenuecatpoc.domain.enums.PlanDuration;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
//빠른 조회를 위한 인덱스 추가
@Table(name = "user_plan",
        indexes = {
                @Index(name = "idx_user_plan_user_expire", columnList = "user_id, expire_at DESC"),
                @Index(name = "idx_user_plan_user_start", columnList = "user_id, start_at DESC")
        }
)
public class UserPlan {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id", nullable = true)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanDuration duration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanStatus status;

    @Column(nullable = false)
    private Instant startAt;

    @Column(nullable = false)
    private Instant expireAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private String transactionId;

    public void changeStatus(PlanStatus status) {
        this.status = status;
    }
}

/*
사용자별 플랜 결제 이력 조회 필드

자세한 설명은 https://www.notion.so/API-ER-2e47c53ee93b8048b657ec634b40b524 참조
 */