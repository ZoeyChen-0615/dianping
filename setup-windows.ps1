# setup-windows.ps1 —— 黑马点评 Windows 一键环境安装脚本（幂等，可重复运行）
#
# 用法：
#   1. 右键 PowerShell →「以管理员身份运行」
#   2. Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
#   3. cd 到本仓库根目录，执行 .\setup-windows.ps1
#
# 做什么：winget 安装 Temurin JDK8 / Git / Maven / MySQL8 / Erlang / RabbitMQ，
# 建 hmdp 库并导入数据，同步 application.yaml 的数据库密码，最后新开窗口启动后端。
# 注意：Redis 官方没有 Windows 版，脚本只做连通性检查，装法见第 4 步提示。

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host ""; Write-Host "########## $msg ##########" -ForegroundColor Cyan }

function Test-Port($computer, $port) {
    try { $c = New-Object Net.Sockets.TcpClient; $c.Connect($computer, $port); $c.Close(); return $true }
    catch { return $false }
}

# 0. 管理员 + winget 检查
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Host "请以管理员身份运行 PowerShell 后再执行本脚本。" -ForegroundColor Red; exit 1 }
if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Host "未找到 winget（需要 Win10 1809+/Win11 的「应用安装程序」），请先安装。" -ForegroundColor Red; exit 1
}

function Ensure-Winget($id, $name, $fallbackUrl) {
    $found = winget list --id $id -e 2>$null | Select-String -SimpleMatch $id -Quiet
    if ($found) { Write-Host "√ $name 已安装，跳过" -ForegroundColor Green; return }
    Write-Host "→ 正在安装 $name ..."
    winget install --id $id -e --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        Write-Host "× $name 自动安装失败，请手动安装：$fallbackUrl" -ForegroundColor Yellow
    }
}

Write-Step "1/6 安装基础软件（winget）"
Ensure-Winget "EclipseAdoptium.Temurin.8.JDK" "Temurin JDK 8" "https://adoptium.net/temurin/releases/?version=8"
Ensure-Winget "Git.Git" "Git" "https://git-scm.com/download/win"
Ensure-Winget "Apache.Maven" "Maven" "https://maven.apache.org/download.cgi"
Ensure-Winget "Oracle.MySQL.8.0" "MySQL 8.0" "https://dev.mysql.com/downloads/installer/"
Ensure-Winget "Erlang.Erlang" "Erlang/OTP（RabbitMQ 依赖）" "https://www.erlang.org/downloads"
Ensure-Winget "RabbitMQ.RabbitMQ" "RabbitMQ Server" "https://www.rabbitmq.com/install-windows.html"
Write-Host "注：MySQL 安装过程中如弹出配置向导，请设置好 root 密码并记住，下面会用到。" -ForegroundColor Yellow

# 刷新当前会话的 PATH（winget 刚装好的软件）
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
            [System.Environment]::GetEnvironmentVariable("Path", "User")

# JAVA_HOME 指向 Temurin 8
$jdk8 = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -like "jdk-8*" } | Sort-Object Name -Descending | Select-Object -First 1
if ($jdk8) {
    $env:JAVA_HOME = $jdk8.FullName
    [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk8.FullName, "Machine")
    Write-Host "√ JAVA_HOME = $($jdk8.FullName)" -ForegroundColor Green
} else {
    Write-Host "× 未找到 Temurin JDK 8，请手动安装并设置 JAVA_HOME 后重跑。" -ForegroundColor Yellow
}

Write-Step "2/6 MySQL：建库并导入数据"
$mysqlSvc = Get-Service -Name "MYSQL80" -ErrorAction SilentlyContinue
if ($mysqlSvc -and $mysqlSvc.Status -ne "Running") { Start-Service $mysqlSvc; Start-Sleep 3 }
$mysqlExe = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysqlExe) { $mysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" }
if (-not (Test-Path $mysqlExe)) { Write-Host "× 找不到 mysql，请确认 MySQL 已安装。" -ForegroundColor Red; exit 1 }

$rootPwd = Read-Host "请输入 MySQL root 密码（安装 MySQL 时设置的；若设的就是 123456 直接回车）"
if ([string]::IsNullOrWhiteSpace($rootPwd)) { $rootPwd = "123456" }
$env:MYSQL_PWD = $rootPwd
& $mysqlExe -u root -e "SELECT 1" 2>$null
if ($LASTEXITCODE -ne 0) { Write-Host "× root 密码不对或 MySQL 未启动，请检查后重跑。" -ForegroundColor Red; exit 1 }
& $mysqlExe -u root -e "CREATE DATABASE IF NOT EXISTS hmdp CHARACTER SET utf8mb4;"
$sqlFile = Join-Path $PSScriptRoot "src\main\resources\db\hmdp.sql"
$tables = (& $mysqlExe -u root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hmdp';").Trim()
if ($tables -eq "0") {
    Get-Content $sqlFile -Raw -Encoding UTF8 | & $mysqlExe -u root --default-character-set=utf8mb4 hmdp
    Write-Host "√ hmdp.sql 导入完成" -ForegroundColor Green
} else {
    Write-Host "√ hmdp 库已有 $tables 张表，跳过导入" -ForegroundColor Green
}
if ($rootPwd -ne "123456") {
    $yaml = Join-Path $PSScriptRoot "src\main\resources\application.yaml"
    (Get-Content $yaml -Raw).Replace("password: 123456", "password: $rootPwd") |
        Set-Content $yaml -NoNewline -Encoding UTF8
    Write-Host "√ application.yaml 数据库密码已同步为你输入的密码" -ForegroundColor Green
}

Write-Step "3/6 RabbitMQ"
$rmqSvc = Get-Service -Name "RabbitMQ" -ErrorAction SilentlyContinue
if ($rmqSvc) {
    if ($rmqSvc.Status -ne "Running") { Start-Service $rmqSvc; Start-Sleep 5 }
    Write-Host "√ RabbitMQ 服务运行中（guest/guest，端口 5672）" -ForegroundColor Green
} else {
    Write-Host "× 未找到 RabbitMQ Windows 服务，请确认已安装（需先装 Erlang）。" -ForegroundColor Yellow
}

Write-Step "4/6 Redis 连通性检查"
if (Test-Port "127.0.0.1" 6379) {
    Write-Host "√ Redis 6379 可连" -ForegroundColor Green
} else {
    Write-Host @"
× 6379 连不上。Redis 官方没有 Windows 发行版，二选一：
  A. 安装 Memurai Developer（免费、Redis 兼容、原生 Windows 服务）：https://www.memurai.com
  B. WSL2 里装 redis，或 Docker：docker run -d -p 6379:6379 redis:7
装好并确认 6379 能连后，重新运行本脚本（已完成的步骤会自动跳过）。
"@ -ForegroundColor Yellow
    exit 1
}

Write-Step "5/6 启动后端"
Set-Location $PSScriptRoot
Write-Host "新开一个 PowerShell 窗口启动后端（mvn spring-boot:run）..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot'; mvn spring-boot:run"
Write-Host ""
Write-Host "看到 Started HmDianPingApplication 即成功：http://localhost:8081" -ForegroundColor Green
Write-Host "前端：用浏览器直接打开 frontend\index.html" -ForegroundColor Green
Write-Host "登录验证码在后端窗口的日志里找「发送登录验证码」。" -ForegroundColor Green
