package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  Service implementation
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Autowired
    private IBlogService blogService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IFollowService followService;

    @Override
    public Result queryById(Integer id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("Blog not found or has been deleted");
        }
        queryBlogUser(blog);
        // Also check whether the current user liked this blog (see isBlogLiked)
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // Query by user
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // Get the current page records
        List<Blog> records = page.getRecords();
        // Load the author info
        records.forEach(blog -> {
            queryBlogUser(blog);
            // Also check whether the current user liked this blog (see isBlogLiked)
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    private void isBlogLiked(Blog blog) {
        // 1. Get the current user
        UserDTO userDTO = UserHolder.getUser();
        // Anonymous visitor: skip the check entirely
        if (userDTO == null) {
            return;
        }

        // 2. Check whether the current user liked it
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userDTO.getId().toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long id) {
        // 1. Get the current user
        Long userId = UserHolder.getUser().getId();
        // 2. Not liked yet: increment the like count and add the user to the set
        String key = BLOG_LIKED_KEY + id;
        // Try to get the score
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        // null means the user is not in the set
        if (score == null) {
            // Increment the like count
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            // Add the user to the set
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
            // 3. Already liked: unlike and remove the user from the set
        }else {
            // Decrement the like count
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success){
                // Remove from the set
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Integer id) {
        String key = BLOG_LIKED_KEY + id;
        // zrange key 0 4 — fetch the top 5 elements of the zset
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        // Empty (nobody liked it yet): return an empty list
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        // Join the ids with ','. SQL IN does not return rows in our order,
        // so sort explicitly with ORDER BY FIELD(id, ...)
        String idsStr = StrUtil.join(",", ids);
        //select * from tb_user where id in (ids[0], ids[1] ...) order by field(id, ids[0], ids[1] ...)
        List<UserDTO> userDTOS = userService.query().in("id", ids)
                .last("order by field(id," + idsStr + ")")
                .list().stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // Get the logged-in user
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // Save the blog post
        boolean isSuccess = blogService.save(blog);
        if (!isSuccess)
        {
            return Result.fail("Failed to publish the post");
        }
        // On success, get the author's ID and look up their followers in tb_follow
//        select * from tb_follower where follow_user_id = ?
        List<Follow> followUsers = followService.query().eq("follow_user_id", user.getId()).list();
        for (Follow follow : followUsers) {
            Long userId = follow.getUserId();
            String key = FEED_KEY+ userId;
            // Fan-out: every follower has their own inbox
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // Return the ID
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1. Get the current user
        Long userId = UserHolder.getUser().getId();
        // 2. Read the user's inbox (key = fixed prefix + user ID) for posts from followed bloggers
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typeTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        // 3. Null/empty check
        if (typeTuples == null || typeTuples.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 4. Parse blogId, minTime (timestamp) and offset; pre-size the list since we know how big it will be
        ArrayList<Long> ids = new ArrayList<>(typeTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typeTuple : typeTuples) {
            // 4.1 Get the ID
            String id = typeTuple.getValue();
            ids.add(Long.valueOf(id));
            // 4.2 Get the score (timestamp)
            long time = typeTuple.getScore().longValue();
            if (time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }
        // Work around SQL IN not preserving order: sort by the given IDs
        String idsStr = StrUtil.join(",");

        // 5. Query the blogs by IDs
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1 Load the author's info
            queryBlogUser(blog);
            // 5.2 Check whether the current user liked it
            isBlogLiked(blog);
        }
        // 6. Wrap up and return
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}
