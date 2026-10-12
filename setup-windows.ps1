# setup-windows.ps1 — one-click environment setup for the Dianping project on Windows.
# Idempotent: safe to re-run; completed steps are skipped automatically.
#
# Usage:
#   1. Right-click PowerShell -> "Run as administrator"
#   2. Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
#   3. cd to this repo root, then run: .\setup-windows.ps1
#
# What it does:
#   - winget: Temurin JDK 8, Git, MySQL 8.0 (pinned), Erlang/OTP 28 (pinned)
#   - direct download: Apache Maven 3.9.9, RabbitMQ 4.1.8 (not on winget)
#   - creates the hmdp database and imports seed data
#   - syncs the MySQL root password into src/main/resources/application.yaml
#   - launches the backend in a new PowerShell window (mvn spring-boot:run)
# Note: Redis has no official Windows build. The script only checks port 6379;
# see step 7 for how to install it (Memurai / WSL2 / Docker).

$ErrorActionPreference = "Stop"

function Write-Step($msg) { Write-Host ""; Write-Host "########## $msg ##########" -ForegroundColor Cyan }

function Test-Port($computer, $port) {
    try { $c = New-Object Net.Sockets.TcpClient; $c.Connect($computer, $port); $c.Close(); return $true }
    catch { return $false }
}

function Add-MachinePath($dir) {
    $mp = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    if ($mp -split ";" -notcontains $dir) {
        [System.Environment]::SetEnvironmentVariable("Path", "$mp;$dir", "Machine")
        Write-Host "√ added to Machine PATH: $dir" -ForegroundColor Green
    }
    if ($env:Path -split ";" -notcontains $dir) { $env:Path += ";$dir" }
}

# 0. Prechecks: admin + winget
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Host "Please re-run PowerShell as Administrator, then run this script again." -ForegroundColor Red; exit 1 }
if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Host "winget not found (needs the 'App Installer' from the Microsoft Store on Win10 1809+/Win11)." -ForegroundColor Red; exit 1
}

function Ensure-Winget($id, $name, $version, $fallbackUrl) {
    $found = winget list --id $id -e 2>$null | Select-String -SimpleMatch $id -Quiet
    if ($found) { Write-Host "√ $name already installed, skipping" -ForegroundColor Green; return }
    Write-Host "→ installing $name ..."
    $verArg = @(); if ($version) { $verArg = @("--version", $version) }
    winget install --id $id -e --silent --accept-package-agreements --accept-source-agreements @verArg
    if ($LASTEXITCODE -ne 0) {
        Write-Host "× $name auto-install failed, please install manually: $fallbackUrl" -ForegroundColor Yellow
    }
}

Write-Step "1/8 Installing base software (winget)"
Ensure-Winget "EclipseAdoptium.Temurin.8.JDK" "Temurin JDK 8" "" "https://adoptium.net/temurin/releases/?version=8"
Ensure-Winget "Git.Git" "Git" "" "https://git-scm.com/download/win"
# Pin MySQL 8.0.x: the script looks for service MYSQL80 and path "MySQL Server 8.0".
# (Oracle.MySQL defaults to 8.4 on winget; old 8.0.x versions stay installable.)
Ensure-Winget "Oracle.MySQL" "MySQL 8.0" "8.0.43" "https://dev.mysql.com/downloads/installer/"
# Pin Erlang 28.x: RabbitMQ 4.1 supports Erlang 26-28 (newer OTP may refuse to boot it).
Ensure-Winget "Erlang.ErlangOTP" "Erlang/OTP 28" "28.5.0.6" "https://www.erlang.org/downloads"

Write-Step "2/8 Installing Maven 3.9.9 (direct download; not on winget)"
$mvnDir = "C:\Program Files\Apache\Maven"
if (-not (Test-Path "$mvnDir\bin\mvn.cmd")) {
    $mvnVer = "3.9.9"
    $zip = "$env:TEMP\apache-maven-$mvnVer-bin.zip"
    Write-Host "→ downloading Apache Maven $mvnVer ..."
    Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/$mvnVer/binaries/apache-maven-$mvnVer-bin.zip" -OutFile $zip
    $tmp = "$env:TEMP\mvn-extract"; if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $tmp -Force
    New-Item -ItemType Directory -Force -Path $mvnDir | Out-Null
    Copy-Item "$tmp\apache-maven-$mvnVer\*" $mvnDir -Recurse -Force
    Remove-Item $tmp -Recurse -Force; Remove-Item $zip -Force
    Write-Host "√ Maven installed to $mvnDir" -ForegroundColor Green
} else {
    Write-Host "√ Maven already installed, skipping" -ForegroundColor Green
}
Add-MachinePath "$mvnDir\bin"

Write-Step "3/8 Installing RabbitMQ 4.1.8 (direct download; not on winget)"
$rmqVer = "4.1.8"
$rmqDir = "C:\Program Files\RabbitMQ Server\rabbitmq_server-$rmqVer"
$rmqSvc = Get-Service -Name "RabbitMQ" -ErrorAction SilentlyContinue
if (-not (Test-Path "$rmqDir\sbin\rabbitmq-service.bat")) {
    $zip = "$env:TEMP\rabbitmq-server-windows-$rmqVer.zip"
    Write-Host "→ downloading RabbitMQ $rmqVer ..."
    Invoke-WebRequest -Uri "https://github.com/rabbitmq/rabbitmq-server/releases/download/v$rmqVer/rabbitmq-server-windows-$rmqVer.zip" -OutFile $zip
    $dest = "C:\Program Files\RabbitMQ Server"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Expand-Archive -Path $zip -DestinationPath $dest -Force
    Remove-Item $zip -Force
    Write-Host "√ RabbitMQ extracted to $rmqDir" -ForegroundColor Green
} else {
    Write-Host "√ RabbitMQ already downloaded, skipping" -ForegroundColor Green
}
# rabbitmq-service.bat needs ERLANG_HOME; refresh it from the Machine env (set by the Erlang installer)
$env:ERLANG_HOME = [System.Environment]::GetEnvironmentVariable("ERLANG_HOME", "Machine")
if (-not $rmqSvc) {
    Write-Host "→ installing RabbitMQ Windows service ..."
    & "$rmqDir\sbin\rabbitmq-service.bat" install
    Start-Sleep 3
    $rmqSvc = Get-Service -Name "RabbitMQ" -ErrorAction SilentlyContinue
}
if ($rmqSvc) {
    if ($rmqSvc.Status -ne "Running") { Start-Service $rmqSvc; Start-Sleep 5 }
    Write-Host "√ RabbitMQ service running (guest/guest, AMQP port 5672)" -ForegroundColor Green
} else {
    Write-Host "× RabbitMQ service not found. Install manually: https://www.rabbitmq.com/install-windows.html (needs Erlang first)." -ForegroundColor Yellow
}

Write-Step "4/8 Refreshing PATH and JAVA_HOME for this session"
$env:Path = [System.Environment]::GetEnvironmentVariable("Path", "Machine") + ";" +
            [System.Environment]::GetEnvironmentVariable("Path", "User")
$jdk8 = Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue |
       Where-Object { $_.Name -like "jdk-8*" } | Sort-Object Name -Descending | Select-Object -First 1
if ($jdk8) {
    $env:JAVA_HOME = $jdk8.FullName
    [System.Environment]::SetEnvironmentVariable("JAVA_HOME", $jdk8.FullName, "Machine")
    Write-Host "√ JAVA_HOME = $($jdk8.FullName)" -ForegroundColor Green
} else {
    Write-Host "× Temurin JDK 8 not found, install it manually and set JAVA_HOME, then re-run." -ForegroundColor Yellow
}

Write-Step "5/8 MySQL: create database and import seed data"
$mysqlSvc = Get-Service -Name "MYSQL8*" | Select-Object -First 1
if ($mysqlSvc -and $mysqlSvc.Status -ne "Running") { Start-Service $mysqlSvc; Start-Sleep 3 }
$mysqlExe = (Get-Command mysql -ErrorAction SilentlyContinue).Source
if (-not $mysqlExe) {
    $mysqlExe = Get-ChildItem "C:\Program Files\MySQL\MySQL Server *\bin\mysql.exe" -ErrorAction SilentlyContinue |
                Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mysqlExe -or -not (Test-Path $mysqlExe)) {
    Write-Host "× mysql.exe not found. If the MySQL Installer only installed the manager GUI, open 'MySQL Installer' from the Start Menu, add MySQL Server 8.0, then re-run this script." -ForegroundColor Red
    exit 1
}
# A fresh silent install usually leaves root passwordless; probe that first, then ask.
$env:MYSQL_PWD = ""
& $mysqlExe -u root -e "SELECT 1" 2>$null
if ($LASTEXITCODE -eq 0) {
    $rootPwd = ""
    Write-Host "√ MySQL root has no password (fresh install)" -ForegroundColor Green
} else {
    $rootPwd = Read-Host "MySQL root password [default 123456, press Enter to accept]"
    if ([string]::IsNullOrWhiteSpace($rootPwd)) { $rootPwd = "123456" }
    $env:MYSQL_PWD = $rootPwd
    & $mysqlExe -u root -e "SELECT 1" 2>$null
    if ($LASTEXITCODE -ne 0) { Write-Host "× wrong root password or MySQL not started, fix it and re-run." -ForegroundColor Red; exit 1 }
}
& $mysqlExe -u root -e "CREATE DATABASE IF NOT EXISTS hmdp CHARACTER SET utf8mb4;"
$sqlFile = Join-Path $PSScriptRoot "src\main\resources\db\hmdp.sql"
$tables = (& $mysqlExe -u root -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='hmdp';").Trim()
if ($tables -eq "0") {
    Get-Content $sqlFile -Raw -Encoding UTF8 | & $mysqlExe -u root --default-character-set=utf8mb4 hmdp
    Write-Host "√ hmdp.sql imported" -ForegroundColor Green
} else {
    Write-Host "√ hmdp already has $tables tables, skipping import" -ForegroundColor Green
}
$yaml = Join-Path $PSScriptRoot "src\main\resources\application.yaml"
(Get-Content $yaml -Raw).Replace("password: 123456", "password: $rootPwd") |
    Set-Content $yaml -NoNewline -Encoding UTF8
Write-Host "√ application.yaml DB password synced" -ForegroundColor Green

Write-Step "6/8 RabbitMQ check"
$rmqSvc = Get-Service -Name "RabbitMQ" -ErrorAction SilentlyContinue
if ($rmqSvc -and $rmqSvc.Status -eq "Running") {
    Write-Host "√ RabbitMQ running" -ForegroundColor Green
} else {
    Write-Host "! RabbitMQ not running; the app starts anyway but flash-sale (seckill) will fail until RabbitMQ is up." -ForegroundColor Yellow
}

Write-Step "7/8 Redis connectivity check"
if (Test-Port "127.0.0.1" 6379) {
    Write-Host "√ Redis on 6379 reachable" -ForegroundColor Green
} else {
    Write-Host @"
× port 6379 unreachable. Redis has no official Windows build; pick one:
  A. Memurai Developer (free, Redis-compatible, native Windows service): https://www.memurai.com
  B. WSL2: install redis there, or Docker: docker run -d -p 6379:6379 redis:7
Once 6379 answers, re-run this script (finished steps are skipped automatically).
"@ -ForegroundColor Yellow
    exit 1
}

Write-Step "8/8 Starting the backend"
Set-Location $PSScriptRoot
Write-Host "Opening a new PowerShell window to run the backend (mvn spring-boot:run) ..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$PSScriptRoot'; mvn spring-boot:run"
Write-Host ""
Write-Host "When you see 'Started HmDianPingApplication', open http://localhost:8081" -ForegroundColor Green
Write-Host "Frontend: open frontend\index.html directly in your browser (file:// works, CORS is enabled)." -ForegroundColor Green
Write-Host "The login verification code is printed in the backend window's log: look for 'Login verification code'." -ForegroundColor Green
