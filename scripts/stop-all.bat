@echo off
chcp 65001 >nul
title StarLinkAttendant - stopping all services
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-all.ps1"
pause
