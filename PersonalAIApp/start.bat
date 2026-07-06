@echo off
chcp 65001 >nul
echo ============================================
echo   AI 对话应用 - Windows 启动脚本
echo ============================================
echo.

echo [检查] 请确保以下服务已启动：
echo   - MySQL (localhost:3306)
echo   - Redis (localhost:6379)
echo   - Ollama (localhost:11434)
echo.

echo [1/2] 启动后端服务 (Spring Boot)...
cd /d "%~dp0BackEnd"
start "AI-Backend" cmd /c "mvnw.cmd spring-boot:run"
cd /d "%~dp0"

echo [2/2] 启动前端服务 (React + Vite)...
cd /d "%~dp0FrontEnd"
start "AI-Frontend" cmd /c "npm run dev"
cd /d "%~dp0"

echo.
echo ============================================
echo   启动完成！
echo   前端: http://localhost:5173
echo   后端: http://localhost:8080
echo ============================================
echo.
echo 注意：首次使用请先执行 init_db.bat 初始化数据库表
pause