package com.timedeal.product.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis에 둔 재고 숫자 (04단계).
 * 품절 요청을 DB에 닿기 전에 돌려보내려고, 주문이 DB 재고를 건드리기 전에 이 숫자부터 줄인다.
 * - 딜 오픈 전에 DB 재고와 같은 값을 미리 넣어 둔다 (load).
 * - DECRBY는 "읽기-빼기-쓰기"를 명령 하나로 처리한다. Redis는 명령을 하나씩 실행하므로 동시에 호출해도 갱신 손실이 없다.
 * - 키가 없으면 Redis는 0에서 뺀 값(음수)을 돌려준다. 그래서 숫자를 넣지 않은 상품은 모두 품절로 처리된다.
 */
@Component
@RequiredArgsConstructor
public class StockCounter {

    private final StringRedisTemplate redisTemplate;

    /** 딜 오픈 전에 재고 숫자를 넣는다 */
    public void load(Long productId, int stock) {
        redisTemplate.opsForValue().set(key(productId), String.valueOf(stock));
    }

    /** quantity만큼 줄이고, 줄인 뒤 남은 숫자를 돌려준다. 음수면 재고가 모자랐다는 뜻이다 */
    public long decrease(Long productId, int quantity) {
        return redisTemplate.opsForValue().decrement(key(productId), quantity);
    }

    public static String key(Long productId) {
        return "stock:product:" + productId;
    }
}
