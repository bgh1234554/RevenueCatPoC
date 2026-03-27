package dartoo.revenuecatpoc.domain;

import dartoo.revenuecatpoc.domain.enums.PlanStatus;
import dartoo.revenuecatpoc.domain.enums.PlanType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PlanType plan = PlanType.FREE;

    private Instant planExpireAt;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PlanStatus planStatus = PlanStatus.EXPIRED;

    public void updatePlan(PlanType plan, PlanStatus status, Instant expireAt) {
        this.plan = plan;
        this.planStatus = status;
        this.planExpireAt = expireAt;
    }
}