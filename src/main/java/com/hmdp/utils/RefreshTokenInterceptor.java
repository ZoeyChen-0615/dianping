package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    // Not autowired: RefreshTokenInterceptor is instantiated manually in WebConfig
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. Get the token from the request header
        String token = request.getHeader("authorization");
        // 2. Blank token: let it through, LoginInterceptor will handle it
        if (StrUtil.isBlank(token)) {
            return true;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        // 3. Load the user data from Redis by token
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        // 4. Unknown user: let it through, LoginInterceptor will handle it
        if (userMap.isEmpty()) {
            return true;
        }
        // 5. Convert the hash data into a UserDTO
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        // 6. Store the user info in ThreadLocal
        UserHolder.saveUser(userDTO);
        // 7. Refresh the token TTL (30 minutes here, tune as needed)
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // Remove the ThreadLocal user to avoid memory leaks
        UserHolder.removeUser();
    }
}
