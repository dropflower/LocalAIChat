@echo off
chcp 65001 >nul
echo ============================================
echo   smart_chat 数据库初始化脚本
echo ============================================
echo.
echo 此脚本将创建 smart_chat 数据库及其数据表
echo.

set /p MYSQL_USER="请输入 MySQL 用户名 (默认 root): "
if "%MYSQL_USER%"=="" set MYSQL_USER=root

set /p MYSQL_PASS="请输入 MySQL 密码: "

echo.
echo 正在创建数据库和表...

mysql -u %MYSQL_USER% -p%MYSQL_PASS% -e "CREATE DATABASE IF NOT EXISTS smart_chat DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mysql -u %MYSQL_USER% -p%MYSQL_PASS% smart_chat < "%~dp0BackEnd\src\main\resources\schema.sql"

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ============================================
    echo   数据库初始化成功！
    echo   数据库: smart_chat
    echo   表: sc_session, sc_message, sc_model_config
    echo ============================================
) else (
    echo.
    echo 初始化失败，请检查 MySQL 连接信息
)

pause