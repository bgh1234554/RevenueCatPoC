package dartoo.revenuecatpoc.service;

import dartoo.revenuecatpoc.domain.UserEntity;
import dartoo.revenuecatpoc.domain.UserPlan;
import dartoo.revenuecatpoc.domain.enums.PlanAction;
import dartoo.revenuecatpoc.domain.enums.PlanDuration;
import dartoo.revenuecatpoc.domain.enums.PlanStatus;
import dartoo.revenuecatpoc.domain.enums.PlanType;
import dartoo.revenuecatpoc.repository.UserPlanRepository;
import dartoo.revenuecatpoc.webhook.RevenueCatWebhookPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserPlanService {

    private final UserPlanRepository userPlanRepository;

    /**
     * Webhook 전용 플랜 업데이트 메서드.
     * 실제 프로젝트의 updatePlan()과 달리 SecurityContextHolder를 사용하지 않고,
     * RevenueCatWebhookService에서 app_user_id로 조회한 UserEntity를 직접 주입받는다.
     *
     * @param user  app_user_id로 조회한 유저
     * @param event RevenueCat Webhook payload의 event 객체
     *              (type, product_id, expiration_at_ms, transaction_id 등 포함)
     */
    public void updatePlanByWebhook(UserEntity user, RevenueCatWebhookPayload.Event event) {
        Instant now = Instant.now();

        // event.type → PlanAction 변환
        PlanAction action = parseAction(event.getType());

        // product_id → PlanDuration 변환 (CANCEL은 product_id가 없을 수 있어 null 허용)
        PlanDuration duration = event.getProduct_id() != null
                ? parseDuration(event.getProduct_id())
                : null;

        // expiration_at_ms → Instant 변환 (CANCEL은 null일 수 있음)
        Instant newExpireAt = event.getExpiration_at_ms() != null
                ? Instant.ofEpochMilli(event.getExpiration_at_ms())
                : null;

        log.info("[Webhook] userId={}, action={}, duration={}, newExpireAt={}",
                user.getId(), action, duration, newExpireAt);

        switch (action) {
            case SUBSCRIBE, RENEW -> {
                // UserPlan 테이블에 새 구독 이력 저장
                // - startAt: 지금 시점 (PoC 단순화, 실제 구현 시 현재 구독 만료일 기준으로 수정 필요)
                // - expireAt: RevenueCat이 계산한 만료일 (expiration_at_ms) 그대로 사용
                // - transactionId: 추후 CANCEL 시 미래 연장분 환불에 사용
                userPlanRepository.save(UserPlan.builder()
                        .user(user)
                        .plan(PlanType.PREMIUM)
                        .duration(duration)
                        .status(PlanStatus.ACTIVE)
                        .startAt(now)
                        .expireAt(newExpireAt)
                        .transactionId(event.getTransaction_id())
                        .build());

                // UserEntity의 플랜 상태도 함께 업데이트 (현재 플랜 조회 시 사용)
                user.updatePlan(PlanType.PREMIUM, PlanStatus.ACTIVE, newExpireAt);
                log.info("[Webhook] 플랜 활성화 완료: userId={}, expireAt={}", user.getId(), newExpireAt);
            }
            case CANCEL -> {
                // 현재 유효한 플랜을 CANCELLED로 마킹
                // - 만료일은 그대로 유지 (취소해도 만료일까지는 서비스 이용 가능)
                // - 실제 구현 시 미래 연장분(futurePlans)도 CANCELLED 처리 + 환불 API 호출 필요
                userPlanRepository
                        .findTopByUser_IdAndStartAtLessThanEqualAndExpireAtAfterAndStatusInOrderByExpireAtDesc(
                                user.getId(), now, now,
                                List.of(PlanStatus.ACTIVE, PlanStatus.CANCELLED))
                        .ifPresent(plan -> plan.changeStatus(PlanStatus.CANCELLED));

                // PlanType은 PREMIUM 유지 (만료일까지는 프리미엄 사용 가능하므로)
                user.updatePlan(PlanType.PREMIUM, PlanStatus.CANCELLED, user.getPlanExpireAt());
                log.info("[Webhook] 플랜 취소 완료: userId={}", user.getId());
            }
            // RevenueCat의 RENEWAL(자동갱신) 등 우리 서비스에 해당 없는 이벤트는 무시
            default -> log.warn("[Webhook] 처리하지 않는 액션: {}", action);
        }
    }

    /**
     * RevenueCat event.type → PlanAction 변환.
     * INITIAL_PURCHASE      → SUBSCRIBE (최초 구독)
     * NON_RENEWING_PURCHASE → RENEW     (연장 결제)
     * CANCELLATION          → CANCEL    (취소)
     */
    private PlanAction parseAction(String eventType) {
        return switch (eventType) {
            case "INITIAL_PURCHASE"       -> PlanAction.SUBSCRIBE;
            case "NON_RENEWING_PURCHASE"  -> PlanAction.RENEW;
            case "CANCELLATION"           -> PlanAction.CANCEL;
            default -> throw new RuntimeException("처리하지 않는 이벤트 타입: " + eventType);
        };
    }

    /**
     * RevenueCat product_id → PlanDuration 변환.
     * RevenueCat 대시보드에서 상품 등록 시 product_id에 "monthly" 또는 "yearly"가
     * 포함되도록 네이밍해야 이 파싱이 정상 동작한다.
     * 예: "premium_monthly" → MONTHLY, "premium_yearly" → YEARLY
     */
    private PlanDuration parseDuration(String productId) {
        if (productId.contains("monthly")) return PlanDuration.MONTHLY;
        if (productId.contains("yearly"))  return PlanDuration.YEARLY;
        throw new RuntimeException("알 수 없는 product_id: " + productId);
    }
}
/**
 * 실제 프로젝트(UserPlanService)와의 차이점
 *
 * [제거한 것]
 * getCurrentUser()          → Webhook은 RevenueCatWebhookService에서 user 직접 주입
 * getSessionEmail()         → SecurityContextHolder 불필요 (세션 없는 서버 간 통신)
 * TokenService              → JWT 발급 불필요 (Webhook 처리는 응답에 토큰 필요 없음)
 * getCurrentPlan()          → PoC 범위 밖
 * getHistory()              → PoC 범위 밖
 * calculateNewExpireAt()    → RevenueCat의 expiration_at_ms를 그대로 사용하므로 불필요
 * validateRenewWindow()     → PoC 범위 밖
 *                             (실제 구현 시 유지 필요 - 예외 발생 시 환불 API 호출로 2차 처리)
 * DTO 클래스들              → Webhook payload에서 파싱한 값을 직접 파라미터로 받음
 * ApiException / ErrorCode  → PoC에서는 RuntimeException으로 충분
 *
 * [PoC 이후 실제 구현 시 수정할 것]
 * startAt → 현재 구독 만료일 기준으로 수정 (RENEW 시 현재 만료일 다음 날부터 시작)
 * CANCEL  → 미래 연장분(futurePlans) CANCELLED 처리 + RevenueCat 환불 API 호출 추가
 * validateRenewWindow() 복구 + Webhook 예외 시 자동 환불 처리 추가
 */