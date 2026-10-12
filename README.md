# dianping（本地可运行版）

黑马点评 —— 简化版大众点评。点评类项目：邮箱验证码登录、探店点评、优惠券秒杀（RabbitMQ + 令牌桶限流）、
每日签到、好友关注、粉丝关注博主后主动推送博客等模块。

本仓库是原项目 `haopengmai/dianping` 的可运行修复版，主要改动见下方「相对原仓库的修复」。

## 本地运行（Mac，Homebrew）

```bash
# 1. 安装依赖
brew install --cask temurin@8   # JDK 8
brew install maven mysql redis rabbitmq
brew services start mysql redis rabbitmq

# 2. 建库并导入数据
mysql -u root -e "CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4;"
mysql -u root hmdp < src/main/resources/db/hmdp.sql

# 3. 按需修改 src/main/resources/application.yaml
#    - MySQL 用户名/密码（默认 root / 123456）
#    - RabbitMQ（默认 localhost:5672，guest/guest）
#    - hmdp.mail.user / hmdp.mail.password（发件 QQ 邮箱 + SMTP 授权码；不配也能跑，见下）

# 4. 启动
export JAVA_HOME=$(/usr/libexec/java_home -v 1.8)
mvn spring-boot:run
```

后端跑在 http://localhost:8081。

## 登录流程

`POST /user/code?phone=你的邮箱` 获取验证码 → `POST /user/login`（body: `{"phone": "...", "code": "..."}`）登录。

- 没配发件邮箱时，`/user/code` 仍返回成功，验证码打印在控制台日志并写入 Redis：
  `redis-cli -n 6 get "login:code:<你的邮箱>"`
- 配了 `hmdp.mail.user` / `hmdp.mail.password`（QQ 邮箱 SMTP 授权码，邮箱设置 → 账户 → 开启 SMTP 获取）
  才会真正发邮件。

## 相对原仓库的修复

| # | 问题 | 修复 |
|---|---|---|
| 1 | `pom.xml` 里 MySQL 驱动写死 5.1.47，连不上 MySQL 8/9（`caching_sha2_password` 鉴权不支持） | 升级到 8.0.33，驱动类改为 `com.mysql.cj.jdbc.Driver` |
| 2 | `application.yaml` 里 RabbitMQ 指向作者的虚拟机 `192.168.88.128`，且端口写成 `15672`（那是管理界面端口，AMQP 协议要用 `5672`），启动连不上 | 改为 `localhost:5672`，`guest/guest` |
| 3 | `MailUtils.java` 硬编码了作者的 QQ 邮箱和 SMTP 授权码（已失效，且是敏感信息） | 改为从 `application.yaml` 的 `hmdp.mail` 读取；未配置时抛明确异常 |
| 4 | 邮件发送失败会导致 `/user/code` 直接 500 | 发送失败只记 warn 日志，验证码已在 Redis，接口正常返回 |
| 5 | `db/hmdp.sql` 里 `tb_seckill_voucher` 用 `timestamp DEFAULT '0000-00-00 00:00:00'`，新版 MySQL 严格模式下建表失败 | 改为 `DEFAULT CURRENT_TIMESTAMP` |
| 6 | `nginx-1.18.0/` 是 Windows 版 nginx 且 html 里只有默认页面，真正的 hmdp 前端页面不在仓库里 | 删除；先用 Postman/curl 直接调 API |

## 技术栈

| 技术 | 说明 |
|---|---|
| SpringBoot 2.3.12 | 容器+MVC框架 |
| Redis（≥6.2） | 分布式缓存；GEOSEARCH（附近商家）需要 6.2+ |
| RabbitMQ | 秒杀异步下单，削峰 |
| MySQL | 关系型数据库 |
| MyBatis-Plus / Lombok / Hutool / Redisson | 常规组件 |
