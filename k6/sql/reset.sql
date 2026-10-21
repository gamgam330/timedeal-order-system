-- 부하 테스트 전에 매번 같은 상태로 되돌린다 (make k6-reset)
TRUNCATE TABLE payment;
TRUNCATE TABLE orders;

-- 상품 1: 타임딜 상품, 재고 100개 (spike 시나리오)
UPDATE product SET stock = 100 WHERE id = 1;

-- 상품 2: 처리량 측정용, 재고가 바닥나지 않도록 넉넉하게 (load 시나리오)
INSERT INTO product (id, name, price, stock, created_at)
VALUES (2, '부하 테스트용 상품', 1000, 10000000, NOW(6))
ON DUPLICATE KEY UPDATE stock = 10000000;
