package dartoo.revenuecatpoc.webhook;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * RevenueCat이 Webhook으로 전송하는 POST 요청의 body를 매핑하는 DTO.
 *
 * RevenueCat은 결제 이벤트가 발생할 때마다 아래 형식의 JSON을 우리 백엔드로 전송한다.
 * {
 *   "api_version": "1.0",
 *   "event": {
 *     "type": "INITIAL_PURCHASE",
 *     "app_user_id": "test@test.com",
 *     "product_id": "premium_monthly",
 *     "expiration_at_ms": 1591726653000,
 *     "transaction_id": "170000869511114",
 *     ...
 *   }
 * }
 */
@Getter
@NoArgsConstructor
public class RevenueCatWebhookPayload {

    // RevenueCat API 버전. 현재 "1.0". 추후 필드 변경 시 이 값을 기준으로 하위 호환성을 관리할 수 있다.
    private String api_version;

    //실제 이벤트 데이터가 담긴 중첩 객체.
    private Event event;

    @Getter
    @NoArgsConstructor
    public static class Event {

        /**
         * 이벤트 타입.
         * 우리 서비스에서 처리하는 타입:
         *   - INITIAL_PURCHASE      : 최초 구독 결제 → SUBSCRIBE 처리
         *   - NON_RENEWING_PURCHASE : 연장 결제 → RENEW 처리
         *   - CANCELLATION          : 구독 취소 → CANCEL 처리
         * 그 외 (RENEWAL, BILLING_ISSUE 등)는 자동갱신 전용이라 무시하고 추후에 구현 시 처리.
         */
        private String type;

        /**
         * RevenueCat에서 사용하는 유저 식별자.
         * 우리 DB의 userEmail과 매핑하여 UserEntity를 조회한다.
         * 추후 프런트에 RevenueCat SDK를 설치할 때
         * Purchases.logIn("test@test.com")과 같이
         * userEmail을 app_user_id로 설정해야 한다.
         */
        private String app_user_id;

        /**
         * 결제한 상품 ID. RevenueCat 대시보드에서 우리가 직접 등록한 이름이다.
         * 예: "premium_monthly" → MONTHLY, "premium_yearly" → YEARLY
         * 이 값을 파싱해서 PlanDuration을 결정한다.
         */
        private String product_id;

        /**
         * 구독 만료 시각 (밀리초 Unix timestamp).
         * RevenueCat이 Apple/Google 기준으로 직접 계산한 값이므로
         * 백엔드에서 별도로 만료일을 계산할 필요 없이 이 값을 그대로 사용한다.
         * Instant.ofEpochMilli(expiration_at_ms)로 변환하여 사용.
         * CANCELLATION 이벤트에서는 null일 수 있다.
         */
        private Long expiration_at_ms;

        /**
         * Apple/Google에서 발급한 결제 고유 식별자.
         * UserPlan 테이블에 저장해두었다가, CANCEL 시 미래 연장분 환불 API 호출에 사용한다.
         * RevenueCat 환불 API: POST /v1/subscribers/{app_user_id}/transactions/{transaction_id}/refund
         */
        private String transaction_id;
    }
}