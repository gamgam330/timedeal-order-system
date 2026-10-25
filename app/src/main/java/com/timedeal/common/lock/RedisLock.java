package com.timedeal.common.lock;

import com.timedeal.common.exception.LockTimeoutException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * Redis 분산 락 (SET NX PX + Lua 해제).
 * - 획득: SET key token NX PX. 키가 없을 때만 저장되므로 여러 서버가 동시에 시도해도 한 명만 성공한다.
 * - TTL: 락을 쥔 서버가 죽어도 LEASE_TIME 뒤에는 풀린다 (영원히 잠기는 것 방지).
 * - 해제: 내 token일 때만 지운다. TTL이 지나 남이 새로 잡은 락을 지우지 않게, 비교와 삭제를 Lua로 한 번에 실행한다.
 * - 대기: 실패하면 잠깐 쉬었다가 다시 시도한다 (스핀 락).
 */
@Component
public class RedisLock {

    private static final Duration LEASE_TIME = Duration.ofSeconds(3);
    private static final Duration WAIT_TIME = Duration.ofSeconds(5);
    private static final long RETRY_INTERVAL_MS = 10;
    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final Timer waitTimer;
    private final Counter attemptCounter;

    public RedisLock(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.waitTimer = Timer.builder("stock.lock.wait")
                .description("락을 얻기까지 기다린 시간")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.attemptCounter = Counter.builder("stock.lock.attempts")
                .description("SET NX 시도 횟수 (실패해서 다시 시도한 것 포함)")
                .register(meterRegistry);
    }

    public <T> T executeWithLock(String key, Supplier<T> action) {
        String token = UUID.randomUUID().toString();
        acquire(key, token);
        try {
            return action.get();
        } finally {
            redisTemplate.execute(UNLOCK_SCRIPT, List.of(key), token);
        }
    }

    private void acquire(String key, String token) {
        long start = System.nanoTime();
        long deadline = start + WAIT_TIME.toNanos();
        while (true) {
            attemptCounter.increment();
            if (Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, token, LEASE_TIME))) {
                waitTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new LockTimeoutException(key);
            }
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockTimeoutException(key);
            }
        }
    }
}
