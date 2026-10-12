# Dianping

A simplified Dianping clone — a Spring Boot learning project. Features: shop browsing/details, vouchers, flash-sale ordering, review blogs, likes, daily check-ins, email-code login.

This repo is a fork of haopengmai/dianping, adapted to run locally (see "Changes from upstream" below).

## Layout

- `src/` — backend (Spring Boot 2.3, port 8081)
- `src/main/resources/db/hmdp.sql` — database seed script
- `frontend/index.html` — frontend (single-file mobile page, open directly in a browser)
- `setup-windows.ps1` — one-click Windows environment setup (run in admin PowerShell)
- `start-backend.bat` — start the backend on Windows (double-click once the environment is ready)

## Requirements

JDK 8, Maven 3.6+, MySQL 8, Redis 6.2+ (needs GEOSEARCH), RabbitMQ 3.x.
Windows note: Redis has no official Windows build — use Memurai / WSL2 / Docker.

## Getting started

**Fresh Windows machine**: right-click PowerShell → Run as Administrator, then
`Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`,
and run `.\setup-windows.ps1` from the repo root (installs JDK 8 / Maven / MySQL / RabbitMQ, creates and seeds the database, starts the backend; install Redis separately when the script prompts).

**Manual setup** (Mac/Linux, or Windows with an existing environment):

1. `CREATE DATABASE hmdp CHARACTER SET utf8mb4;`, then import `src/main/resources/db/hmdp.sql`.
2. Adjust `src/main/resources/application.yaml` to your machine (MySQL password, Redis, RabbitMQ).
3. Start from the project root: on Windows double-click `start-backend.bat` or run `mvn spring-boot:run`; on Mac/Linux run `mvn spring-boot:run`. `Started HmDianPingApplication` means success.
4. Open `frontend/index.html` directly in a browser (CORS is already configured).

## Login

Email-code login. If the email never arrives after clicking "send code", that's expected — the code is printed in the backend log: on Mac/Linux `grep 'Login verification code' <log> | tail -1`, on Windows use PowerShell `Select-String`. Tokens last 30 minutes and live in browser localStorage.

## Changes from upstream

Email-code login; `tb_user.phone` widened to varchar(64); email regex allows dots; new CorsConfig; LoginInterceptor lets OPTIONS preflights through; `/blog/{id}` added to the auth exclusion list; MySQL driver 8.0.33; RabbitMQ on localhost:5672; zero-date defaults removed from tb_seckill_voucher; mail failures only logged; portable relative upload dir; removed the nginx directory and the duplicate Lombok.

## Things to try

Home shows 10 categories ("Food & Drink" has 9 shops, "KTV" has 5, the rest are empty in the seed data — that's normal); shop details → vouchers; Blogs → blog details → likes (login required); Profile → check-in / logout. For flash sales, first create a voucher via `POST /voucher/seckill`, then grab it.
