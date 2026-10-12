# 黑马点评 dianping

简化版大众点评 —— Spring Boot 学习项目。功能：商户浏览/详情、优惠券、秒杀下单、探店博客、点赞、签到、邮箱验证码登录。

本仓库是 haopengmai/dianping 的 fork，做了本地可运行改造（见文末）。

## 目录结构

- `src/` —— 后端（Spring Boot 2.3，端口 8081）
- `src/main/resources/db/hmdp.sql` —— 数据库初始化脚本
- `frontend/index.html` —— 前端（单文件手机端页面，直接用浏览器打开）
- `start-backend.bat` —— Windows 一键启动后端

## 环境要求

JDK 8、Maven 3.6+、MySQL 8、Redis 6.2+（需 GEOSEARCH）、RabbitMQ 3.x。
Windows 注意：Redis 官方无 Windows 版，用 WSL2 / Docker / Memurai。

## 启动步骤

1. `CREATE DATABASE hmdp CHARACTER SET utf8mb4;` 后导入 `src/main/resources/db/hmdp.sql`。
2. 按本机情况改 `src/main/resources/application.yaml`（MySQL 密码、Redis、RabbitMQ）。
3. 项目根目录启动：Windows 双击 `start-backend.bat` 或 `mvn spring-boot:run`；Mac/Linux 跑 `mvn spring-boot:run`。看到 `Started HmDianPingApplication` 即成功。
4. 浏览器直接打开 `frontend/index.html`（后端已配跨域）。

## 登录说明

用邮箱验证码登录。点「发验证码」后邮件发不出去是正常的，验证码在后端日志里：Mac/Linux 用 `grep '发送登录验证码' 日志 | tail -1`，Windows 用 PowerShell `Select-String`。token 30 分钟有效，存浏览器 localStorage。

## 相对原项目的改动

邮箱验证码登录；`tb_user.phone` 加宽到 varchar(64)；邮箱正则允许带点；新增 CorsConfig；LoginInterceptor 放行 OPTIONS 预检；`/blog/{id}` 加入登录放行名单；MySQL 驱动 8.0.33；RabbitMQ 改 localhost:5672；tb_seckill_voucher 去零日期默认值；邮件发送失败只记日志；上传目录改跨平台相对路径；删 nginx 目录和重复 Lombok。

## 测试建议

首页 10 个分类（「美食」9 家、「KTV」5 家，其余种子数据为空属正常）；商户详情→优惠券；探店→博客详情→点赞（需登录）；我的→签到/退出。秒杀需先 `POST /voucher/seckill` 建券再抢购。
