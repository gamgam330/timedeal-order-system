-- 부하 테스트 후 정합성 검사 (make k6-check)
-- 남은 재고 + 판매 수량 = 초기 재고 여야 한다. 다르면 데이터가 깨진 것이다.
SELECT p.id                                      AS product_id,
       CASE p.id WHEN 1 THEN 100 ELSE 10000000 END AS initial_stock,
       p.stock                                   AS remaining_stock,
       COALESCE(SUM(o.quantity), 0)              AS sold_quantity,
       COUNT(o.id)                               AS paid_orders,
       p.stock + COALESCE(SUM(o.quantity), 0)
           - CASE p.id WHEN 1 THEN 100 ELSE 10000000 END AS diff_should_be_0
FROM product p
         LEFT JOIN orders o ON o.product_id = p.id AND o.status = 'PAID'
GROUP BY p.id, p.stock
ORDER BY p.id;
