package com.starlink.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类。
 * <p>
 * 所有业务模块共用，封装常用 set/get/delete/expire 等操作。
 * 基于 StringRedisTemplate，键值均为字符串。
 */
@Service
@RequiredArgsConstructor
public class RedisService {

    private final StringRedisTemplate stringRedisTemplate;

    /** 写入（带过期时间） */
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    /** 写入（永久，除非显式 expire） */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /** 读取 */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /** 删除 */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /** 是否存在 */
    public Boolean exists(String key) {
        Boolean hasKey = stringRedisTemplate.hasKey(key);
        return Boolean.TRUE.equals(hasKey);
    }

    /** 给已有 key 设过期时间 */
    public void expire(String key, Duration ttl) {
        stringRedisTemplate.expire(key, ttl.toSeconds(), TimeUnit.SECONDS);
    }

    /** 查剩余过期秒数（-1 永久，-2 不存在） */
    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }
}