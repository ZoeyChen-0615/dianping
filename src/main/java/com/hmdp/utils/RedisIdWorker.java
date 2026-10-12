package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

// Generate a globally unique ID
@Component
public class RedisIdWorker {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    // Epoch start: 2022-01-01 00:00:00
    public static final Long BEGIN_TIMESTAMP = 1640995200L;
    // Sequence bit length
    public static final Long COUNT_BIT = 32L;

    public long nextId(String keyPrefix){
        // 1. Generate the timestamp
        LocalDateTime now = LocalDateTime.now();
        long currentSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = currentSecond - BEGIN_TIMESTAMP;
        // 2. Generate the sequence number
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("inc:"+keyPrefix+":"+date);
        // 3. Combine and return via bit operations
        return timeStamp << COUNT_BIT | count;
    }
}
