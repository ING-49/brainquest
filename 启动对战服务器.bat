@echo off
chcp 65001 >nul
title BrainQuest PK Server :8765
cd /d "%~dp0update-server"

echo ==============================================
echo   BrainQuest 联机对战服务器
echo ==============================================
echo   [模拟器]  ws://10.0.2.2:8765
echo.
echo   [手机-同一Wi-Fi] 使用下面的局域网地址:
powershell -NoProfile -Command "Get-NetIPAddress -AddressFamily IPv4 | Where-Object {$_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*'} | ForEach-Object { '     ws://' + $_.IPAddress + ':8765' }"
echo.
echo   手机端: App 大厅 → 联机对战 → 修改服务器地址 → 创建/加入房间
echo   首次启动如弹出防火墙提示, 请点"允许访问"
echo   按 Ctrl+C 停止
echo ==============================================
where python >nul 2>nul || (echo [错误] 未找到 python & pause & exit /b 1)
python -c "import websockets" 2>nul || pip install websockets
python pk_server.py 8765
pause
