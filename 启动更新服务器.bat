@echo off
chcp 65001 >nul
title BrainQuest Update Server :8000
cd /d "%~dp0update-server"

echo ==============================================
echo   BrainQuest 更新服务器
echo ==============================================
echo.
echo   [模拟器]  http://10.0.2.2:8000
echo.
echo   [手机-同一Wi-Fi] 使用下面的局域网地址:
powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'} | ForEach-Object { '     http://' + $_.IPAddress + ':8000' }"
echo.
echo   手机端操作: App 设置 → 更新服务器地址 → 填上面地址 → 保存
echo   首次启动如弹出 Windows 防火墙提示, 请点"允许访问"
echo   按 Ctrl+C 停止服务
echo ==============================================
echo.
where python >nul 2>nul || (echo [错误] 未找到 python, 请先安装 & pause & exit /b 1)
python -m http.server 8000
pause
