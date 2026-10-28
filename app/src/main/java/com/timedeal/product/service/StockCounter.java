package com.timedeal.product.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis에 둔 재고 숫자 (04단계).
 * 품절 요청을 DB에 닿기 전에 돌려보내려고, 주문이 DB 재고를 건드리기 전에 이 숫자부터 줄인다.
 * - 딜 오픈 전에 DB 재고와 같은 값을 미리 넣어 둔다 (load).
 * - 줄이기는 Lua 스크립트로 "남은 수량 확인 + 줄이기"를 한 번에 실행한다 (Redis판 원자적 UPDATE).
 *   Redis는 스크립트 하나를 끝까지 실행한 뒤 다음 명령을 처리하므로, 확인과 줄이기 사이에 다른 요청이 끼어들지 않는다.
 *   DECRBY 후 음수면 INCRBY로 되돌리는 방식은 그 사이 숫자가 잠깐 음수로 보여서, 재고가 남았는데 다른 주문을 거절했다. (troubleshooting 004)
 * - 키가 없으면 품절로 처리한다. 숫자를 넣지 않은 상품은 팔지 않는다.
 */
@Component
@RequiredArgsConstructor
public class StockCounter {

    // 남은 수량이 충분하면 줄이고 1, 모자라거나 키가 없으면 그대로 두고 0
    private static final RedisScript<Long> DECREASE_SCRIPT = new DefaultRedisScript<>(
            "local stock = tonumber(redis.call('get', KEYS[1]))\n"
                    + "if stock == nil or stock < tonumber(ARGV[1]) then return 0 end\n"
                    + "redis.call('decrby', KEYS[1], ARGV[1])\n"
                    + "return 1",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 딜 오픈 전에 재고 숫자를 넣는다 */
    public void load(Long productId, int stock) {
        redisTemplate.opsForValue().set(key(productId), String.valueOf(stock));
    }

    /** quantity만큼 줄인다. 모자라면 숫자를 건드리지 않고 false를 돌려준다 */
    public boolean tryDecrease(Long productId, int quantity) {
        Long result = redisTemplate.execute(DECREASE_SCRIPT, List.of(key(productId)), String.valueOf(quantity));
        return Long.valueOf(1).equals(result);
    }

    /** 결제 실패 등으로 예약을 취소할 때 되돌린다 */
    public void increase(Long productId, int quantity) {
        redisTemplate.opsForValue().increment(key(productId), quantity);
    }

    /** 지금 남은 숫자. 키가 없으면 null */
    public Long current(Long productId) {
        String value = redisTemplate.opsForValue().get(key(productId));
        return value == null ? null : Long.valueOf(value);
    }

    public static String key(Long productId) {
        return "stock:product:" + productId;
    }
}
