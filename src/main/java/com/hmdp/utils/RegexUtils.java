package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;

/**
 * @author 虎哥
 */
public class RegexUtils {
    /**
     * Whether the phone format is invalid
     * @param phone phone number to validate
     * @return true if valid, false otherwise
     */
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }
    /**
     * Whether the email format is invalid
     * @param email email to validate
     * @return true if valid, false otherwise
     */
    public static boolean isEmailInvalid(String email){
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * Whether the verification code format is invalid
     * @param code verification code to validate
     * @return true if valid, false otherwise
     */
    public static boolean isCodeInvalid(String code){
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    // Check against the regex
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}
