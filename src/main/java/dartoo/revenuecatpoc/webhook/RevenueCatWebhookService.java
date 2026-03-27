package dartoo.revenuecatpoc.webhook;

import dartoo.revenuecatpoc.domain.UserEntity;
import dartoo.revenuecatpoc.repository.UserEntityRepository;
import dartoo.revenuecatpoc.service.UserPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


/**
 * RevenueCat Webhook 이벤트 처리 서비스.
 *
 * 컨트롤러로부터 Webhook 요청을 받아 아래 순서로 처리한다.
 * 1. Secret 검증  - 요청이 RevenueCat에서 온 것인지 확인
 * 2. 유저 조회    - payload의 app_user_id(= userEmail)로 DB에서 유저 조회
 * 3. 이벤트 위임  - UserPlanService.updatePlanByWebhook()으로 실제 플랜 업데이트
 *
 * RevenueCat 이벤트 타입 → 내부 처리 매핑:
 *   INITIAL_PURCHASE      → SUBSCRIBE (최초 구독)
 *   NON_RENEWING_PURCHASE → RENEW     (연장 결제)
 *   CANCELLATION          → CANCEL    (구독 취소)
 *   TEST                  → 무시      (대시보드 연결 확인용)
 *   그 외                 → 무시      (자동갱신 전용 이벤트 등)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevenueCatWebhookService {
    private final UserEntityRepository userEntityRepository;
    private final UserPlanService userPlanService;

    @Value("${revenuecat.webhook.secret}")
    private String webhookSecret;

    /**
     * Webhook 이벤트 처리 메인 메서드.
     * 1. Secret 검증 → 2. 유저 조회 → 3. UserPlanService 호출
     */
    @Transactional
    public void handleEvent(RevenueCatWebhookPayload payload, String authorization) {
        // 1. Secret 검증
        // RevenueCat이 보낸 요청인지 확인. 불일치 시 처리 거부.
        validateSecret(authorization);

        RevenueCatWebhookPayload.Event event = payload.getEvent();
        log.info("[Webhook] 이벤트 수신: type={}, app_user_id={}", event.getType(), event.getApp_user_id());

        // 2. ngrok을 이용해 RevenueCat에서 수신되는 test webhook의 경우에는 event type이 TEST이다.
        // TEST 이벤트는 단순 연결 확인용이므로 DB 처리 없이 바로 반환한다.
        if ("TEST".equals(event.getType())) {
            log.info("[Webhook] TEST 이벤트 수신 확인 완료");
            return;
        }

        // 3. app_user_id(= userEmail)로 유저 조회
        // 앱에서 로그인 후 Purchases.logIn(userEmail)로 SDK를 초기화하기로 약속했으므로
        // app_user_id와 userEmail이 항상 일치한다.
        UserEntity user = userEntityRepository.findByUserEmail(event.getApp_user_id())
                .orElseThrow(() -> new RuntimeException(
                        "유저를 찾을 수 없습니다. app_user_id=" + event.getApp_user_id()
                                + " (data.sql의 userEmail과 일치하는지 확인)"));

        // 4. UserPlanService에 이벤트 처리 위임
        userPlanService.updatePlanByWebhook(user, event);
    }

    /**
     * Authorization 헤더 값이 우리가 설정한 Secret과 일치하는지 검증.
     * JWT와 달리 서명 검증이나 만료 개념 없이 단순 문자열 비교.
     * Secret을 모르는 외부 서버는 이 엔드포인트를 악용할 수 없다.
     */
    private void validateSecret(String authorization) {
        if (!webhookSecret.equals(authorization)) {
            log.warn("[Webhook] Secret 불일치 - 요청 거부");
            throw new RuntimeException("유효하지 않은 Webhook Secret입니다.");
        }
    }
}
