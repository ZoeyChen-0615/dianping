package com.hmdp.controller;


import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.entity.UserInfo;
import com.hmdp.service.IUserInfoService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MailUtils;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  REST controller
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * Send a login verification code
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }

    /**
     * Login
     * @param loginForm login params: email + code, or email + password
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        // Login logic
        return userService.login(loginForm,session);
    }

    /**
     * Logout
     * @return nothing
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO: implement logout
        return Result.fail("Not implemented");
    }

    @GetMapping("/me")
    public Result me(){
        // Return the currently logged-in user
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // Look up the profile
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // No profile yet, probably the first visit
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // Return
        return Result.ok(info);
    }

    @GetMapping("/{id}")
    public Result queryById(@PathVariable("id") Long userId) {
        // Look up the profile
        User user = userService.getById(userId);
        if (user == null) {
            // No profile yet, probably the first visit
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // Return
        return Result.ok(userDTO);
    }

    // Check in
    @PostMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    // Monthly check-in stats
    @GetMapping("/sign/count")
    public Result signCount(){
        return userService.signCount();
    }
}
