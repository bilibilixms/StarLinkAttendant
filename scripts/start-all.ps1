# ============================================================
# StarLinkAttendant 一键启动（RAG + 后端 + 管理端）
# 用法：双击 start-all.bat
# 已在运行的服务自动跳过；API Key 未配置时会提示输入一次
# ============================================================

$ErrorActionPreference = 'Continue'

$ModelDir   = "c:\Users\LIRAN\Downloads\模型下载目录\模型下载目录"
$BackendDir = "e:\StarLinkAttendant\BackEnd\Backend"
$MilvusDir  = "e:\StarLinkAttendant\docker\milvus"
$AdminDir   = "e:\StarLinkAttendant\resource\FrontEnd\StarLinkAttendant"
$Mvn        = "E:\develop\IDEA\IntelliJ IDEA 2025.1.1.1\plugins\maven\lib\maven3\bin\mvn.cmd"
$DockerExe  = "C:\Program Files\Docker\Docker\resources\bin\docker.exe"

function Test-Port([int]$p) {
    $c = New-Object Net.Sockets.TcpClient
    try { $c.Connect('127.0.0.1', $p); return $true } catch { return $false }
    finally { $c.Close() }
}

function Wait-Port([int]$p, [string]$name, [int]$seconds) {
    Write-Host "   等待 $name (端口 $p) 就绪" -NoNewline
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Port $p) { Write-Host " ... 完成" -ForegroundColor Green; return $true }
        Write-Host "." -NoNewline
        Start-Sleep -Seconds 3
    }
    Write-Host " ... 超时！" -ForegroundColor Red
    return $false
}

function Start-Detail([string]$title, [string]$workDir, [string]$cmdLine) {
    Start-Process powershell -ArgumentList '-NoExit', '-Command',
        "`$host.UI.RawUI.WindowTitle='$title'; cd '$workDir'; $cmdLine"
}

Write-Host "========== StarLinkAttendant 一键启动 ==========" -ForegroundColor Cyan

# --- 0. 基础服务检测 ---
if (Test-Port 3306) { Write-Host "[OK] MySQL 3306 已运行" -ForegroundColor Green }
else { Write-Host "[警告] MySQL 3306 未监听，请先启动 MySQL80 服务" -ForegroundColor Yellow }

if (Test-Port 6379) { Write-Host "[OK] Redis 6379 已运行" -ForegroundColor Green }
else { Write-Host "[警告] Redis 6379 未监听，后端缓存/登录可能受影响" -ForegroundColor Yellow }

# --- 1. Milvus（Docker） ---
if (Test-Port 19530) {
    Write-Host "[跳过] Milvus 19530 已在运行" -ForegroundColor Green
} else {
    Write-Host ">> 检查 Docker Desktop..." -ForegroundColor Cyan
    if (-not (Get-Process "Docker Desktop" -ErrorAction SilentlyContinue)) {
        Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        Write-Host "   Docker Desktop 已启动，等待引擎初始化..."
    }
    $deadline = (Get-Date).AddSeconds(120)
    $ok = $false
    while ((Get-Date) -lt $deadline) {
        & $DockerExe info *> $null
        if ($LASTEXITCODE -eq 0) { $ok = $true; break }
        Start-Sleep -Seconds 5
    }
    if (-not $ok) {
        Write-Host "[失败] Docker 引擎 2 分钟内未就绪，跳过 Milvus" -ForegroundColor Red
    } else {
        Write-Host "   Docker 引擎就绪，启动 Milvus 容器..."
        Push-Location $MilvusDir
        & $DockerExe compose up -d
        Pop-Location
        [void](Wait-Port 19530 "Milvus" 300)
    }
}

# --- 2. Embedding / Rerank Python 服务 ---
if (-not (Test-Port 8001)) {
    Write-Host ">> 启动 Embedding 服务 (8001)..." -ForegroundColor Cyan
    Start-Detail "emb-8001" $ModelDir ".\.venv\Scripts\python.exe emb_server.py"
}
if (-not (Test-Port 8002)) {
    Write-Host ">> 启动 Rerank 服务 (8002)..." -ForegroundColor Cyan
    Start-Detail "rerank-8002" $ModelDir ".\.venv\Scripts\python.exe rerank_server.py"
}
if (-not (Test-Port 8001)) { [void](Wait-Port 8001 "Embedding" 180) }
if (-not (Test-Port 8002)) { [void](Wait-Port 8002 "Rerank" 180) }

# --- 3. 后端（8080） ---
if (Test-Port 8080) {
    Write-Host "[跳过] 后端 8080 已在运行" -ForegroundColor Green
} else {
    if (-not $env:AI_API_KEY) {
        $env:AI_API_KEY = Read-Host "请输入 DeepSeek API Key（sk- 开头，直接回车则本次不启用 AI）"
    }
    if (-not $env:MYSQL_PASSWORD) {
        $env:MYSQL_PASSWORD = Read-Host "请输入 MySQL root 密码"
    }
    Write-Host ">> 启动后端 (8080)..." -ForegroundColor Cyan
    Start-Detail "backend-8080" $BackendDir "& '$Mvn' spring-boot:run -pl starlink-starter '-Dspring-boot.run.profiles=dev' '-Dmaven.test.skip=true'"
    [void](Wait-Port 8080 "后端" 240)
}

# --- 4. 管理端（5173） ---
if (Test-Port 5173) {
    Write-Host "[跳过] 管理端 5173 已在运行" -ForegroundColor Green
} else {
    Write-Host ">> 启动管理端 (5173)..." -ForegroundColor Cyan
    Start-Detail "admin-5173" $AdminDir "npm run dev"
    [void](Wait-Port 5173 "管理端" 120)
}

# --- 汇总 ---
Write-Host ""
Write-Host "========== 启动状态汇总 ==========" -ForegroundColor Cyan
$items = @(
    @('MySQL', 3306), @('Redis', 6379), @('Milvus', 19530),
    @('Embedding', 8001), @('Rerank', 8002), @('后端', 8080), @('管理端', 5173)
)
foreach ($s in $items) {
    if (Test-Port $s[1]) { Write-Host ("[运行中] {0} : {1}" -f $s[0], $s[1]) -ForegroundColor Green }
    else { Write-Host ("[未启动] {0} : {1}" -f $s[0], $s[1]) -ForegroundColor Red }
}
Write-Host ""
Write-Host "管理端: http://localhost:5173  （账号 13800000001 / 密码 123456）"
Write-Host "各服务运行在弹出的独立窗口中，关闭窗口或运行 stop-all.bat 即可停止对应服务"
