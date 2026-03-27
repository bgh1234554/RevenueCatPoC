package dartoo.revenuecatpoc.webhook;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * RevenueCat Webhook 수신 컨트롤러.
 *
 * RevenueCat은 결제 이벤트(구독, 연장, 취소 등)가 발생할 때마다
 * 사전에 등록된 URL로 HTTP POST 요청을 자동으로 보낸다.
 * 이 컨트롤러는 그 요청을 받아서 RevenueCatWebhookService로 처리를 위임한다.
 *
 * 엔드포인트: POST /api/webhooks/revenuecat
 * 인가 방식: JWT 없이 Authorization 헤더의 Secret 값으로 처리
 *           (SecurityConfig에서 permitAll로 열고, 내부에서 Secret 검증)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/webhooks")
public class RevenueCatWebhookController {
    private final RevenueCatWebhookService revenueCatWebhookService;

    /**
     * RevenueCat이 결제 이벤트 발생 시 호출하는 Webhook 엔드포인트.
     * SecurityConfig에서 permitAll()로 열어두되, 내부에서 Secret으로 인가 처리.
     *
     * @param payload       RevenueCat이 보내는 JSON body
     * @param authorization RevenueCat 대시보드에서 설정한 Secret 값
     *                      (RevenueCat은 매 요청마다 Authorization 헤더에 이 값을 담아 보냄)
     */
    @PostMapping("/revenuecat")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody RevenueCatWebhookPayload payload,
            @RequestHeader("Authorization") String authorization) {

        revenueCatWebhookService.handleEvent(payload, authorization);
        return ResponseEntity.ok().build();
    }
}
