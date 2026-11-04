-- 05단계: 주문 100만 건을 만든다 (make seed)
-- - 사용자 1만 명(user_id 1~10,000)에 고르게 흩어진다. 한 사람당 평균 100건
-- - 상태는 PAID 약 80%, FAILED 약 20%
-- - 약 1년 동안 30초마다 한 건씩 들어온 것처럼 created_at을 매긴다 (id가 클수록 최근 주문)
-- - 번호 n을 CRC32로 섞어서 사용자와 상태를 정한다. 실행할 때마다 같은 데이터가 나온다
--   (RAND(n)은 n이 1씩 커질 때 값이 거의 일정하게 움직여서, 사용자가 규칙적으로 돌고 상태가 사용자별로 굳어 버렸다)
-- - 지난 주문은 상품 3(지난 주문용)에 몰아 둔다. 상품 1, 2의 재고 검사(check.sql)에 섞이지 않게 하려는 것이다
TRUNCATE TABLE payment;
TRUNCATE TABLE orders;

INSERT INTO product (id, name, price, stock, created_at)
VALUES (3, '지난 주문용 상품', 1000, 0, NOW(6))
ON DUPLICATE KEY UPDATE stock = 0;

INSERT INTO orders (user_id, product_id, quantity, total_price, status, created_at)
SELECT 1 + CRC32(CONCAT('user-', n)) % 10000,
       3,
       1,
       1000,
       IF(CRC32(CONCAT('status-', n)) % 100 < 80, 'PAID', 'FAILED'),
       TIMESTAMP('2025-11-01 00:00:00') + INTERVAL n * 30 SECOND
FROM (SELECT a.d + b.d * 10 + c.d * 100 + d.d * 1000 + e.d * 10000 + f.d * 100000 AS n
      FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) a,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) b,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) c,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) d,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) e,
           (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) f) nums
ORDER BY n;

-- 통계를 새로 매겨 둔다. 옵티마이저가 실행 계획을 고를 때 쓰는 행 수 추정치다
ANALYZE TABLE orders;

SELECT COUNT(*) AS orders, COUNT(DISTINCT user_id) AS users,
       ROUND(AVG(status = 'PAID') * 100, 1) AS paid_pct
FROM orders;
