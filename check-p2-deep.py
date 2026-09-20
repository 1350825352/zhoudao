#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
P2 视频识别系统深度调查脚本
目标: 定位飞行任务执行时前端视频画面"一闪一闪"的根因
远程主机: 192.168.2.208 (humh01 / 1qaz2wsx)
容器: p2-video
"""
import sys
import io
import paramiko

HOST = "192.168.2.208"
USER = "humh01"
PASS = "1qaz2wsx"
CONTAINER = "p2-video"

# 让 Windows 终端正确显示中文
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")


def make_client():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASS, timeout=20)
    return client


def run(client, cmd, label=None, max_bytes=200_000):
    """执行远程命令,返回 stdout 文本"""
    if label:
        print("\n" + "=" * 80)
        print(f"[{label}]")
        print(f"$ {cmd}")
        print("=" * 80)
    stdin, stdout, stderr = client.exec_command(cmd, timeout=60)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    if max_bytes and len(out) > max_bytes:
        out = out[:max_bytes] + f"\n... [截断,完整长度 {len(out)} 字节]\n"
    if out:
        print(out)
    if err.strip():
        print("--- stderr ---")
        print(err)
    return out


def dex(client, inner_cmd, label=None):
    """在 p2-video 容器内执行命令"""
    full = f'docker exec {CONTAINER} sh -c "{inner_cmd}"'
    return run(client, full, label)


def main():
    print(f"连接 {USER}@{HOST} ...")
    client = make_client()

    # 0. 基础环境
    run(client, "hostname && date && docker ps --filter name=p2-video --format '{{.Names}} {{.Status}} {{.Image}}'",
        label="0. 主机/容器状态")

    # 1. 前端目录结构
    dex(client, "ls -la /app/frontend", label="1.1 前端目录")
    dex(client, "find /app/frontend -maxdepth 3 -type f \\( -name '*.js' -o -name '*.jsx' -o -name '*.ts' -o -name '*.tsx' -o -name '*.vue' -o -name '*.html' \\) | head -100",
        label="1.2 前端源文件列表")

    # 1.3 任务状态轮询逻辑定位
    dex(client, "grep -rnE 'setInterval|setTimeout|/api/v1/video/tasks|axios|fetch\\(|useQuery|react-query|polling' /app/frontend/src 2>/dev/null | head -80",
        label="1.3 前端轮询关键字")
    dex(client, "grep -rnE 'tasks/task_|/tasks/|task_status|taskStatus|getTask|fetchTask|pollTask' /app/frontend/src 2>/dev/null | head -80",
        label="1.4 任务相关关键字")
    dex(client, "grep -rnE 'GET /api/v1/video/tasks|/api/v1/video/tasks' /app/frontend 2>/dev/null | head -40",
        label="1.5 调用接口位置")

    # 1.6 视频组件渲染相关
    dex(client, "grep -rnE 'video|<video|VideoPlayer|stream|WebSocket|ws://|wss://' /app/frontend/src 2>/dev/null | head -80",
        label="1.6 视频组件/WebSocket 关键字")

    # 2. uvicorn 错误日志全文
    dex(client, "wc -l /var/log/uvicorn-err.log && echo '---' && cat /var/log/uvicorn-err.log 2>/dev/null | tail -400",
        label="2.1 uvicorn-err.log (尾部400行)")
    dex(client, "grep -nE 'SIGSEGV|CUDA|OutOfMemory|out of memory|RuntimeError|core dumped|segfault|ERROR|Traceback|cuMemAlloc|Watchdog|killed' /var/log/uvicorn-err.log 2>/dev/null | tail -120",
        label="2.2 uvicorn 关键错误行")

    # 3. 后端目录与任务 API
    dex(client, "ls -la /app/app && echo '---' && find /app/app -maxdepth 3 -type f -name '*.py' | head -80",
        label="3.1 后端结构")
    dex(client, "grep -rnE 'tasks/\\{|/tasks/\\?|task_id|task_status|@router|@app|websocket|WebSocket' /app/app 2>/dev/null | head -120",
        label="3.2 后端任务API/WebSocket路由")
    dex(client, "grep -rln 'websocket\\|WebSocket' /app/app 2>/dev/null",
        label="3.3 WebSocket 文件列表")

    # 4. nginx error & access
    dex(client, "wc -l /var/log/nginx/error.log /var/log/nginx/access.log 2>/dev/null",
        label="4.1 nginx 日志行数")
    dex(client, "tail -120 /var/log/nginx/error.log 2>/dev/null",
        label="4.2 nginx error.log 尾部")
    dex(client, "grep -E 'upgrade|websocket|WebSocket|101|499|502|504|500' /var/log/nginx/access.log 2>/dev/null | tail -80",
        label="4.3 nginx access 关键请求")

    # 5. supervisord 日志
    dex(client, "tail -150 /var/log/supervisord.log 2>/dev/null",
        label="5.1 supervisord 日志")

    # 6. GPU 状态(宿主机)
    run(client, "nvidia-smi", label="6.1 nvidia-smi")
    run(client, "nvidia-smi --query-gpu=index,name,memory.total,memory.used,memory.free,utilization.gpu,utilization.memory --format=csv",
        label="6.2 GPU 显存/利用率")
    run(client, "nvidia-smi --query-compute-apps=pid,process_name,used_memory --format=csv 2>/dev/null",
        label="6.3 占用 GPU 的进程")

    # 7. 进程/重启次数
    dex(client, "ps -ef | grep -E 'uvicorn|gunicorn|python|nginx|node' | grep -v grep",
        label="7.1 容器内进程")
    run(client, "docker inspect p2-video --format '{{.RestartCount}} restarts; State={{.State.Status}}; StartedAt={{.State.StartedAt}}; OOMKilled={{.State.OOMKilled}}; ExitCode={{.State.ExitCode}}'",
        label="7.2 容器重启信息")

    # 8. WebSocket 断连/重连日志
    dex(client, "grep -rnE 'websocket|WebSocket|ws://|wss://|disconnect|onclose|reconnect|broken pipe|ConnectionClosed' /app/app /var/log 2>/dev/null | grep -iE 'close|disconnect|reconnect|broken|error|fail' | tail -60",
        label="8.1 WebSocket 断连/重连痕迹")
    dex(client, "grep -rnE 'websocket|WebSocket' /var/log/uvicorn-err.log /var/log/nginx/error.log /var/log/supervisord.log 2>/dev/null | tail -60",
        label="8.2 日志中的 WebSocket 记录")

    # 9. 前端构建产物 + index.html (确认是否 SPA 轮询)
    dex(client, "ls -la /app/frontend/dist 2>/dev/null; ls /app/frontend/build 2>/dev/null; ls /app/frontend/static 2>/dev/null",
        label="9.1 前端构建产物")
    dex(client, "find /app/frontend -maxdepth 2 -name 'index.html' -exec cat {} \\; 2>/dev/null | head -80",
        label="9.2 index.html 内容")

    client.close()
    print("\n=== 调查完成 ===")


if __name__ == "__main__":
    main()
