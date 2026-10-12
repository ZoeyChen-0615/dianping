package com.hmdp.utils;

import java.io.File;

public class SystemConstants {
    // 上传目录：项目运行目录下的 uploads，跨平台（Windows/Mac/Linux 通用）
    public static final String IMAGE_UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";
    public static final String USER_NICK_NAME_PREFIX = "user_";
    public static final int DEFAULT_PAGE_SIZE = 5;
    public static final int MAX_PAGE_SIZE = 10;
}
