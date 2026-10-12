package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  Service implementation
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFellow) {
        // Get the current user ID
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        // Check whether this is a follow or unfollow
        if (isFellow) {
            // Follow: save to the database
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean successed = save(follow);
            // Also write to Redis
            if (successed) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // Unfollow: remove from the database
//            delete from tb_follow where user_id = ?  and follow_user_id = ?
            boolean successed=remove(new QueryWrapper<Follow>().eq("user_id", userId)
                    .eq("follow_user_id",followUserId));
            // Also remove from Redis
            if (successed){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        // Get the logged-in user's ID
        Long userId = UserHolder.getUser().getId();
//        select count(*) from tb_follow where user_id = ?  and follow_user_id = ?
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();
        // Only need existence, so count(*) is enough
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        // Get the current user ID
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + id;
        String key2 = "follows:" + userId;
        // Intersect the follow lists of the current user and the blogger
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            // No intersection: return an empty list
            return Result.ok(Collections.emptyList());
        }
        // Convert the result to a list
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // Load the common-followed users by IDs and wrap as UserDTOs
        List<UserDTO> userDTOS = userService.listByIds(ids).stream().map(user ->
                BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOS);
    }
}
