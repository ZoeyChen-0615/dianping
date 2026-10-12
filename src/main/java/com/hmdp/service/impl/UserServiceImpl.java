package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MailUtils;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  Service implementation
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    // Send the verification code
    @Override
    public Result sendCode(String phone, HttpSession session) throws MessagingException {
        // 1. Check the tier-1 rate limit
        Boolean oneLevelLimit = stringRedisTemplate.opsForSet().isMember(ONE_LEVERLIMIT_KEY + phone, "1");
        if (oneLevelLimit != null && oneLevelLimit) {
            // Tier-1 limited: no code can be sent
            return Result.fail("Please wait 5 minutes before requesting again");
        }

// 2. Check the tier-2 rate limit
        Boolean twoLevelLimit = stringRedisTemplate.opsForSet().isMember(TWO_LEVERLIMIT_KEY + phone, "1");
        if (twoLevelLimit != null && twoLevelLimit) {
            // Tier-2 limited: no code can be sent
            return Result.fail("Please wait 20 minutes before requesting again");
        }

// 3. Count codes sent in the last minute
        long oneMinuteAgo = System.currentTimeMillis() - 60 * 1000;
        long count_oneminute = stringRedisTemplate.opsForZSet().count(SENDCODE_SENDTIME_KEY + phone, oneMinuteAgo, System.currentTimeMillis());
        if (count_oneminute >= 1) {
            // Already sent once in the last minute
            return Result.fail("Less than a minute since the last code, please retry in a minute");
        }

        // 4. Count codes sent in the last 5 minutes
        long fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000;
        long count_fiveminute = stringRedisTemplate.opsForZSet().count(SENDCODE_SENDTIME_KEY + phone, fiveMinutesAgo, System.currentTimeMillis());
        if (count_fiveminute % 3 == 2 && count_fiveminute > 5) {
            // Sent 8, 11, 14, ... times: escalate to tier-2 limit
            stringRedisTemplate.opsForSet().add(TWO_LEVERLIMIT_KEY + phone, "1");
            stringRedisTemplate.expire(TWO_LEVERLIMIT_KEY + phone, 20, TimeUnit.MINUTES);
            return Result.fail("Please wait 20 minutes before requesting another code");
        } else if (count_fiveminute == 5) {
            // Sent 5 times in 5 minutes: apply tier-1 limit
            stringRedisTemplate.opsForSet().add(ONE_LEVERLIMIT_KEY + phone, "1");
            stringRedisTemplate.expire(ONE_LEVERLIMIT_KEY + phone, 5, TimeUnit.MINUTES);
            return Result.fail("Sent 5 times in 5 minutes, please wait 5 minutes before retrying");
        }

          // Generate the code
        String code = MailUtils.achieveCode();

         // Store the code in Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("Login verification code: {}", code);
         // Send the code (no working SMTP in this demo env: failures are only logged, the code is still issued)
        try {
            MailUtils.sendtoMail(phone, code);
        } catch (Exception e) {
            log.warn("Mail send failed (ignored in demo env), code written to Redis/log: {}", code);
        }

        // Record the send time and count
        stringRedisTemplate.opsForZSet().add(SENDCODE_SENDTIME_KEY + phone, System.currentTimeMillis() + "", System.currentTimeMillis());

        return Result.ok();
}

    // Login / sign-up
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        // Validate the email; every request must be re-validated
        if(RegexUtils.isEmailInvalid(phone))
        {
            // Invalid: return immediately
            return Result.fail("Invalid email format");
        }
        // Read the code from Redis and verify it
        String Cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        // 1. Validate the email
        if (RegexUtils.isEmailInvalid(phone)) {
            return Result.fail("Invalid email format");
        }
        // 2. Reject mismatched codes
        if (Cachecode==null || !code.equals(Cachecode))
        {
            return Result.fail("Invalid verification code");
        }
        // All good: look up the user in the database

        //select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();

        // Check whether the user exists
        if (user==null)
        {
            user = createuser(phone);
        }
        // Save the user info to Redis
        String token = UUID.randomUUID().toString();

        // 7.2 Convert the UserDTO into a hash map
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        HashMap<String, String > userMap = new HashMap<>();
        userMap.put("id", String.valueOf(userDTO.getId()));
        userMap.put("nickName", userDTO.getNickName());
        userMap.put("icon", userDTO.getIcon());


        // 7.3 Store it
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);

        // 7.4 Token TTL: 30 minutes
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 7.5 Delete the code after a successful login
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        // 8. Return the token
        return Result.ok(token);
    }

    private User createuser(String phone) {
        // Create the user
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX +RandomUtil.randomString(10));
        // Save the user: insert into tb_user(phone,nick_name) values(?,?)
        save(user);
        return user;
    }

    @Override
    public Result sign() {
        // 1. Get the current user
        Long userId = UserHolder.getUser().getId();
        // 2. Get the date
        LocalDateTime now = LocalDateTime.now();
        // 3. Build the key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. Day of month (1-31)
        int dayOfMonth = now.getDayOfMonth();
        // 5. Write to Redis: BITSET key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1. Get the current user
        Long userId = UserHolder.getUser().getId();
        // 2. Get the date
        LocalDateTime now = LocalDateTime.now();
        // 3. Build the key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4. Day of month (1-31)
        int dayOfMonth = now.getDayOfMonth();


        // 5. Read the check-in bits up to today: BITFIELD key GET uDay 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }
        // 6. Walk the bits
        int count = 0;
        Long num = result.get(0);
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else
                count++;
            // Shift right, dropping the last bit
            num = num>>>1;
        }
        return Result.ok(count);
    }
}
