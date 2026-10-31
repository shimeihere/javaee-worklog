@echo off
setlocal
title 周览 Worklog - 打包

set "JDK_HOME=D:\jdkk"
set "MVN_CMD=D:\MAVEN\apache-maven-3.9.14-bin\apache-maven-3.9.14\bin\mvn.cmd"
cd /d "%~dp0"

echo ============================================================
echo   周览 Worklog  ——  只打包（部署给老师用）
echo ============================================================
if not exist "%MVN_CMD%" (
    echo [错误] 找不到 Maven：%MVN_CMD%
    goto :fail
)
set "JAVA_HOME=%JDK_HOME%"
set "PATH=%JDK_HOME%\bin;%PATH%"

rem 不执行 clean：避免删掉 IDEA 正在使用的 target 目录；需要全新构建时手动删 target 文件夹即可
call "%MVN_CMD%" -o package -DskipTests
if errorlevel 1 goto :fail

if not exist "%~dp0dist" mkdir "%~dp0dist"
copy /y "%~dp0target\worklog.jar" "%~dp0dist\worklog.jar" >nul
echo.
echo ------------------------------------------------------------
echo   打包成功： %~dp0target\worklog.jar
echo   另存一份： %~dp0dist\worklog.jar
echo.
echo   这个 jar 自带 Tomcat，对方机器装 JDK 17+ 即可运行：
echo       java -jar worklog.jar
echo   换端口：java -jar worklog.jar --server.port=8090
echo   改数据库：java -jar worklog.jar --spring.datasource.url=jdbc:mysql://IP:3306/worklog
echo ------------------------------------------------------------
echo.
pause
exit /b 0

:fail
echo.
echo [错误] 打包失败，见上面报错。
pause
exit /b 1
