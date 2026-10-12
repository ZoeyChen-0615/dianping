@echo off
REM Windows one-click start for dianping backend (requires JDK8 / Maven / MySQL / Redis / RabbitMQ)
cd /d %~dp0
mvn spring-boot:run
