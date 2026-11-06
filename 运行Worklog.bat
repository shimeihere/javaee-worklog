@echo off
setlocal enabledelayedexpansion
title 周览 Worklog - 运行中（关掉本窗口即停止）

rem ===== 路径配置 =====
set "JDK_HOME=D:\jdkk"
set "MVN_CMD=D:\MAVEN\apache-maven-3.9.14-bin\apache-maven-3.9.14\bin\mvn.cmd"
set "NPM_CMD=D:\npm.cmd"

rem 端口：双击 = 8080；命令行可传参，如  运行Worklog.bat 8081
set "PORT=%~1"
if "%PORT%"=="" set "PORT=8080"

cd /d "%~dp0"
set "JAR=%~dp0target\worklog.jar"
set "STATIC=%~dp0src\main\resources\static"

echo ============================================================
echo   周览 Worklog  ——  一键运行     端口：%PORT%
echo ============================================================

if not exist "%JDK_HOME%\bin\java.exe" (
    echo [错误] 找不到 JDK：%JDK_HOME%
    echo        请修改本文件里的 JDK_HOME 变量。
    goto :fail
)
if not exist "%MVN_CMD%" (
    echo [错误] 找不到 Maven：%MVN_CMD%
    echo        请修改本文件里的 MVN_CMD 变量。
    goto :fail
)
if not exist "%NPM_CMD%" set "NPM_CMD=npm"
set "JAVA_HOME=%JDK_HOME%"
set "PATH=%JDK_HOME%\bin;%PATH%"

rem ===== 1. 端口是否被占（常见原因：IDEA 里那个实例还在跑）=====
echo [1/5] 检查端口 %PORT% ...
set "BUSY="
for /f "tokens=*" %%L in ('netstat -ano -p tcp ^| findstr ":%PORT%" ^| findstr "LISTENING"') do set "BUSY=%%L"
if defined BUSY (
    echo       [占用] 端口 %PORT% 已经有程序在监听：
    echo       !BUSY!
    echo.
    echo       如果那是 IDEA 里正在跑的同一个工程，直接用它就行，不必再启动：
    echo           http://localhost:%PORT%/
    echo       要停掉它：IDEA 里点红色方块，或任务管理器结束 java.exe。
    echo       或者换端口运行：  运行Worklog.bat 8081
    goto :fail
)
echo       端口空闲。

rem ===== 2. 检查 MySQL（3306）=====
echo [2/5] 检查 MySQL ...
powershell -NoProfile -Command "try { $c = New-Object Net.Sockets.TcpClient; $ok = $c.ConnectAsync('127.0.0.1',3306).Wait(1500); $c.Close(); if ($ok) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
if errorlevel 1 (
    echo       [警告] 本机 3306 没开，数据库可能没启动，3 秒后仍继续 ...
    echo              启动命令（管理员）： net start MySQL97
    ping -n 4 127.0.0.1 >nul
) else (
    echo       MySQL 正常。
)

rem ===== 3. 构建前端（npm run build）并同步到 static =====
echo [3/5] 构建前端页面 ...
if not exist "%~dp0frontend\node_modules" (
    echo       首次运行，正在安装前端依赖（约 1-3 分钟）...
    pushd "%~dp0frontend"
    call "%NPM_CMD%" install --no-audit --no-fund
    if errorlevel 1 (
        popd
        echo [错误] npm install 失败，检查 Node.js 是否安装。
        goto :fail
    )
    popd
)
pushd "%~dp0frontend"
call "%NPM_CMD%" run build
if errorlevel 1 (
    popd
    echo [错误] 前端构建失败，报错见上面几行。
    goto :fail
)
popd
if exist "%STATIC%" rd /s /q "%STATIC%"
robocopy "%~dp0frontend\dist" "%STATIC%" /E /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 (
    echo [错误] 前端产物拷贝到 static 失败。
    goto :fail
)
rem 清掉上一轮残留的旧 hash 资源，避免打进 jar
if exist "%~dp0target\classes\static" rd /s /q "%~dp0target\classes\static"
echo       前端已构建并同步到 static。

rem ===== 4. 重新打包（离线构建，改过的代码自动生效）=====
echo [4/5] 编译打包后端（离线，约 10 秒）...
call "%MVN_CMD%" -o -q -DskipTests package
if errorlevel 1 (
    echo.
    echo [错误] 编译失败，报错见上面几行。把报错发给 AI 或老师。
    goto :fail
)
if not exist "%JAR%" (
    echo [错误] 没生成 %JAR%
    goto :fail
)
echo       打包完成（jar 里已包含前端页面）。

rem ===== 5. 启动 =====
echo [5/5] 启动服务 ...
echo ------------------------------------------------------------
echo   前端页面：   http://localhost:%PORT%/        （直接进登录页）
echo   接口地址：   http://localhost:%PORT%/api     （所有接口统一 /api 前缀）
echo   登录接口：   POST /api/auth/login   {"username":"admin","password":"123456"}
echo   停止：       本窗口按 Ctrl+C，或直接关掉本窗口
echo ------------------------------------------------------------
echo.
if "%PORT%"=="8080" (
    "%JDK_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%JAR%"
) else (
    "%JDK_HOME%\bin\java.exe" -Dfile.encoding=UTF-8 -jar "%JAR%" --server.port=%PORT%
)

echo.
echo 服务已停止。
pause
exit /b 0

:fail
echo.
pause
exit /b 1