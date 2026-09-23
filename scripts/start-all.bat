@echo off
chcp 65001 >nul
title StarLinkAttendant - starting all services
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1"
pause
