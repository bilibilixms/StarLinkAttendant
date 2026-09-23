# ============================================================
# StarLinkAttendant 一键停止
# 用法：双击 stop-all.bat
# 说明：Milvus 用 compose down 停止并保留数据卷（知识库文档不丢）
#       MySQL / Redis 为系统服务，保持运行不动
# ============================================================

$ErrorActionPreference = 'Continue'
$MilvusDir = "e:\StarLinkAttendant\docker\milvus"
$DockerExe = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"

function Test-Port([int]$p) {
    $c = New-Object Net.Sockets.TcpClient
    try { $c.Connect('127.0.0.1', $p); return $true } catch { return $false }
    finally { $c.Close() }
}

Write-Host "========== StarLinkAttendant 一键停止 ==========" -ForegroundColor Cyan

# --- 1. 停止后端（连 mvn 启动器一起结束进程树） ---
$stopped = @()
foreach ($p in 8080, 5173, 8001, 8002) {
    if (Test-Port $p) {
        $conns = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
        foreach ($procId in ($conns.OwningProcess | Select-Object -Unique)) {
            Write-Host ("   结束 PID {0}（端口 {1}）" -f $procId, $p)
            taskkill /PID $procId /T /F *> $null
            if ($LASTEXITCODE -eq 0) { $stopped += $p }
        }
    } else {
        Write-Host ("   端口 {0} 未在运行，跳过" -f $p)
    }
}

# --- 2. 停止 Milvus 容器（保留数据） ---
if (Test-Path $DockerExe) {
    & $DockerExe ps -q *> $null
    if ($LASTEXITCODE -eq 0) {
        Write-Host ">> 停止 Milvus 容器（保留数据卷）..."
        Push-Location $MilvusDir
        & $DockerExe compose down *> $null
        Pop-Location
        Write-Host "   容器已停止，知识库数据保留" -ForegroundColor Green
        Write-Host ">> 退出 Docker Desktop..."
        Get-Process "Docker Desktop", "com.docker.backend" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
        Start-Sleep -Seconds 3
        wsl --shutdown 2>$null
        Write-Host "   Docker / WSL 已关闭" -ForegroundColor Green
    }
}

# --- 3. 汇总 ---
Write-Host ""
Write-Host "========== 停止结果 ==========" -ForegroundColor Cyan
foreach ($p in 8080, 5173, 8001, 8002, 19530) {
    if (Test-Port $p) { Write-Host ("[仍在运行] 端口 {0}（可能为其他程序，请手动确认）" -f $p) -ForegroundColor Yellow }
    else { Write-Host ("[已停止]   端口 {0}" -f $p) -ForegroundColor Green }
}
Write-Host "MySQL / Redis 为系统服务，保持运行。"
