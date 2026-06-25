package com.paike.scheduler.auth;

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 简单的内存限流器，用于登录接口防暴力破解。
 * 生产环境建议替换为 Redis + 滑动窗口方案。
 *
 * <p>内存增长保护：
 * <ul>
 *   <li>过期清理：每次记录/检查时，清理超过 {@link #TTL_MS}（15 分钟）的旧记录。</li>
 *   <li>容量上限：Map size 超过 {@link #MAX_CAPACITY}（10000）时，按最旧优先策略淘汰。</li>
 * </ul>
 */
@Service
public class RateLimiterService {

    /** 记录过期时间：15 分钟。超过此时间未被访问的 key 将被清理。 */
    private static final long TTL_MS = 15 * 60 * 1000L;
    /** Map 容量上限，防止无限增长导致 OOM。 */
    private static final int MAX_CAPACITY = 10_000;

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupAt = new AtomicLong(0);

    /**
     * @param key 限流标识（如 "login:username" 或 "login:ip"）
     * @param maxAttempts 时间窗口内最大尝试次数
     * @param windowMs 时间窗口（毫秒）
     * @return true 表示被限流，false 表示允许通过
     */
    public boolean isRateLimited(String key, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
        cleanupExpiredKeysIfDue(now, windowMs);
        enforceCapacity();
        Deque<Long> timestamps = attempts.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxAttempts) {
                return true;
            }
            timestamps.offerLast(now);
            return false;
        }
    }

    private void cleanupExpiredKeysIfDue(long now, long windowMs) {
        long cleanupIntervalMs = Math.max(1_000, Math.min(windowMs, 60_000));
        long previous = lastCleanupAt.get();
        if (now - previous < cleanupIntervalMs) {
            return;
        }
        if (lastCleanupAt.compareAndSet(previous, now)) {
            cleanupExpiredKeys(now, windowMs);
        }
    }

    private void cleanupExpiredKeys(long now, long windowMs) {
        // 同时按限流窗口与 TTL 两个维度清理：
        // 1) 超过 windowMs 的窗口内记录需要剔除；
        // 2) 整个 key 超过 TTL_MS 未活动则移除，防止长期堆积。
        long ttlThreshold = now - TTL_MS;
        attempts.forEach((attemptKey, timestamps) -> {
            synchronized (timestamps) {
                while (!timestamps.isEmpty()
                        && (now - timestamps.peekFirst() > windowMs
                        || timestamps.peekFirst() < ttlThreshold)) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    attempts.remove(attemptKey, timestamps);
                }
            }
        });
    }

    /**
     * 容量上限保护：当 Map size 超过 {@link #MAX_CAPACITY} 时，淘汰最旧的若干记录。
     * 采用“最旧优先”策略——按各 key 队列首元素时间戳升序排序，清空最旧的直到回到上限以内。
     */
    private void enforceCapacity() {
        if (attempts.size() <= MAX_CAPACITY) {
            return;
        }
        // 选取最旧的 MAX_CAPACITY 之外的 key 并清空。为避免在并发下反复触发，
        // 一次性把超出部分按最旧时间戳顺序移除。
        attempts.entrySet().stream()
                .map(e -> {
                    Long first = e.getValue().peekFirst();
                    return Map.entry(e.getKey(), first == null ? Long.MAX_VALUE : first);
                })
                .sorted(Map.Entry.comparingByValue())
                .limit(Math.max(0, attempts.size() - MAX_CAPACITY))
                .forEachOrdered(entry -> {
                    Deque<Long> ts = attempts.get(entry.getKey());
                    if (ts != null) {
                        synchronized (ts) {
                            ts.clear();
                        }
                        attempts.remove(entry.getKey(), ts);
                    }
                });
    }
}
