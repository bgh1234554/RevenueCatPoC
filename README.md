# RevenueCat Webhook PoC

RevenueCat Webhook 수신 → H2 DB 업데이트 검증용 PoC 프로젝트.

실제 모바일 앱 없이 Webhook을 수동 발송하여 구독 결제 백엔드 로직(구독/연장/취소)을 검증한다.

---

## 기술 스택

| | |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5.5 |
| DB | H2 인메모리 |
| ORM | Spring Data JPA |
| 기타 | Lombok, spring-dotenv |

---

## 프로젝트 구조

```
src/main/java/dartoo/revenuecatpoc
├── config
│   └── SecurityConfig.java          # Webhook + H2 콘솔 permitAll, JWT 없음
├── domain
│   ├── UserEntity.java              # 유저 (userEmail, plan, planStatus, planExpireAt)
│   ├── UserPlan.java                # 구독 이력 (startAt, expireAt, transactionId)
│   └── enums
│       ├── PlanAction.java          # SUBSCRIBE, RENEW, CANCEL
│       ├── PlanDuration.java        # MONTHLY, YEARLY, TRIAL
│       ├── PlanStatus.java          # ACTIVE, CANCELLED, EXPIRED
│       └── PlanType.java            # FREE, PREMIUM
├── repository
│   ├── UserEntityRepository.java
│   └── UserPlanRepository.java
├── service
│   └── UserPlanService.java         # updatePlanByWebhook()
└── webhook
    ├── RevenueCatWebhookController.java   # POST /api/webhooks/revenuecat
    ├── RevenueCatWebhookService.java      # Secret 검증 + 이벤트 파싱
    └── RevenueCatWebhookPayload.java      # Webhook body DTO

src/main/resources
├── application.yml
├── data.sql                         # 테스트 유저 초기 데이터
└── static
    └── webhook-test.html            # 브라우저에서 Webhook 수동 발송 UI
```

---

## 실행 방법

### 1. 환경 변수 설정

프로젝트 루트에 `.env` 파일 생성:

```
REVENUECAT_WEBHOOK_SECRET=sample-secret-1116
```

### 2. 서버 실행

```bash
./gradlew bootRun
```

서버가 뜨면 `data.sql`이 자동 실행되어 테스트 유저(`test@test.com`)가 DB에 삽입된다.

---

## 테스트 방법

### 로컬 테스트 (ngrok 불필요)

1. 서버 실행 후 브라우저에서 `http://localhost:8080/webhook-test.html` 접속
2. 이벤트 타입, app_user_id(`test@test.com`), product_id, 만료일 설정 후 전송
3. `http://localhost:8080/h2-console` 에서 DB 변경 확인
   - JDBC URL: `jdbc:h2:mem:poc`
   - User Name: `sa` / Password: (없음)

### RevenueCat 대시보드 연결 확인 (ngrok 필요)

1. `ngrok http 8080` 실행
2. RevenueCat 대시보드 → Webhooks → URL에 `https://{ngrok주소}/api/webhooks/revenuecat` 등록
3. "Send test event" 클릭 → 서버 콘솔에서 TEST 이벤트 수신 확인

> RevenueCat 대시보드의 테스트 이벤트는 payload를 커스텀할 수 없다.
> 실제 구독/연장/취소 플로우 검증은 위의 로컬 테스트를 사용한다.

---

## 처리하는 Webhook 이벤트

| RevenueCat 이벤트 | 처리 |
|---|---|
| `INITIAL_PURCHASE` | 유저 상태 확인 후 SUBSCRIBE 또는 RENEW |
| `NON_RENEWING_PURCHASE` | 유저 상태 확인 후 SUBSCRIBE 또는 RENEW |
| `CANCELLATION` | 현재 플랜 + 미래 연장분 CANCELLED 마킹 |
| `TEST` | 무시 (200 OK 반환) |
| 그 외 | 무시 (log.warn) |

> `event.type`으로 SUBSCRIBE/RENEW를 구분하지 않는다.
> 만료 후 재구매 시에도 `NON_RENEWING_PURCHASE`가 발생하므로,
> 백엔드가 유저의 현재 플랜 상태를 직접 확인하여 판단한다.

---

## 핵심 설계 결정

- **app_user_id = userEmail** — RevenueCat SDK 초기화 시 userEmail을 app_user_id로 설정
- **만료일** — RevenueCat payload의 `expiration_at_ms`를 그대로 사용 (별도 계산 없음).
  단, Apple/Google 모두 해당 결제 건의 만료일만 제공하며 연속 결제 이어 붙이기는
  백엔드가 직접 처리해야 한다. iOS의 경우 Apple이 비자동갱신 구독의 기간을
  직접 계산하지 않으므로 실제 앱 연동 단계에서 샌드박스 테스트로 검증 필요.
- **RENEW 시 startAt** — 현재 구독 만료일(`currentExpireAt`)을 startAt으로 저장. `now`로 저장하면 CANCEL 쿼리가 미래 연장분을 현재 플랜으로 잘못 인식함
- **인가** — RevenueCat이 보내는 `Authorization` 헤더의 Secret 값으로 처리

---

## 실제 프로젝트 이관 시 변경할 것

| | PoC | 실제 프로젝트 |
|---|---|---|
| 유저 조회 | `app_user_id → findByUserEmail()` | `SecurityContextHolder → JWT` |
| 만료일 | `expiration_at_ms` 직접 사용 | 동일 (기존 `calculateNewExpireAt()` 제거) |
| DB | H2 인메모리 | MySQL |
| 환불 처리 | CANCELLED 마킹만 | RevenueCat 환불 API 호출 |
| 검증 로직 | 없음 | `validateRenewWindowAndUniqueness()` 유지 |

추가 구현 필요:
- `transactionId` 기반 RevenueCat 환불 API 연동 (`POST /v1/subscribers/{appUserId}/transactions/{transactionId}/refund`)
- Webhook 예외 발생 시 자동 환불 처리