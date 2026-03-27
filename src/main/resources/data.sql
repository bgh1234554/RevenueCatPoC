-- 테스트용 유저 초기 데이터
-- app_user_id = userEmail 규칙에 따라 RevenueCat Webhook의 event.app_user_id와 매핑됨

INSERT INTO user_entity (user_email, plan, plan_status, plan_expire_at)
VALUES ('test@test.com', 'FREE', 'EXPIRED', NULL);