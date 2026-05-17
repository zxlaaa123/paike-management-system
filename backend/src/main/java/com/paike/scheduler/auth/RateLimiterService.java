package com.paike.scheduler.auth;

import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 简单的内存限流器，用于登录接口防暴力破解。
 * 生产环境建议替换为 Redis + 滑动窗口方案。
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * @param key 限流标识（如 "login:username" 或 "login:ip"）
     * @param maxAttempts 时间窗口内最大尝试次数
     * @param windowMs 时间窗口（毫秒）
     * @return true 表示被限流，false 表示允许通过
     */
    public boolean isRateLimited(String key, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
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
}
