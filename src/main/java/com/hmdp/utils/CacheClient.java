package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Component
public class CacheClient {
    // Changes by Yan Kun
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

// Method 1: serialize any Java object to JSON and store it under a String key with a TTL
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }
// Method 2: same, but with a logical expiration to guard against cache breakdown
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        RedisData<Object> redisData = new RedisData<>();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
// Method 3: fetch by key and deserialize to the given type; guards against cache penetration by caching nulls
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // Check Redis first; the key is a fixed prefix + ID
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // Cache hit: convert to R and return directly
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }

        // Otherwise query the database via the injected fallback function
        R r = dbFallback.apply(id);

        // Miss: cache the null marker in Redis
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // Hit: serialize to JSON
        String jsonStr = JSONUtil.toJsonStr(r);
        // Store in Redis with a TTL
        this.set(key, jsonStr, time, timeUnit);
        // Finally return the result to the caller
        return r;
    }

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // 1. Read the cache from Redis
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. Miss: return null
        if (StrUtil.isBlank(json)) {
            return null;
        }
        // 3. Hit: deserialize the JSON
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4. Check whether it is expired
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5. Not expired: return directly
            return r;
        }
        // 6. Expired: try to acquire the mutex
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        // 7. Lock acquired
        if (flag) {
            // 8. Start a background thread
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R tmp = dbFallback.apply(id);
                    this.setWithLogicExpire(key, tmp, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
            // 9. Return directly
            return r;
        }
        // 10. Lock not acquired: return directly
        return r;
    }

    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit timeUnit) {
        // Check Redis first; the key is a fixed prefix + ID
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // Cache hit: convert to the target type and return directly
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if (json != null) {
            return null;
        }
        R r = null;
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            // Acquire the mutex
            boolean flag = tryLock(lockKey);
            if (!flag) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, timeUnit);
            }
            r = dbFallback.apply(id);
            // Miss: cache the null marker in Redis
            if (r == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // Store in Redis with a TTL
            this.set(key, r, time, timeUnit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
