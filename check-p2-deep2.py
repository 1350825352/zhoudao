#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
P2 第二轮深度调查: 前端 dist 内的轮询逻辑 + 后端源码细节
"""
import sys
import io
import paramiko

HOST = "192.168.2.208"
USER = "humh01"
PASS = "1qaz2wsx"
CONTAINER = "p2-video"

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")


def make_client():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASS, timeout=20)
    return c


def run(c, cmd, label=None, max_bytes=120_000):
    if label:
        print("\n" + "=" * 80)
        print(f"[{label}]")
        print(f"$ {cmd}")
        print("=" * 80)
    stdin, stdout, stderr = c.exec_command(cmd, timeout=60)
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


def dex(c, inner, label=None):
    return run(c, f'docker exec {CONTAINER} sh -c "{inner}"', label)


def main():
    c = make_client()

    # ===== A. 定位前端 index.html 引用的入口 JS =====
    dex(c, "cat /app/frontend/dist/index.html",
        label="A.1 当前 index.html")

    # ===== B. 在 dist/assets 里搜索轮询逻辑 =====
    # 多个 setInterval / 5000 / 500 / axios / fetch 等关键字
    dex(c, "grep -lE 'setInterval|setTimeout' /app/frontend/dist/assets/*.js | head",
        label="B.1 含轮询的 JS 文件")
    # 在 JS 里抓 setInterval/setTimeout 上下文
    dex(c, "grep -oE '.{120}setInterval.{200}' /app/frontend/dist/assets/*.js 2>/dev/null | head -30",
        label="B.2 setInterval 上下文")
    # 任务详情页相关的 TaskDetail 文件
    dex(c, "ls /app/frontend/dist/assets/TaskDetail*.js 2>/dev/null | tail -3",
        label="B.3 最新 TaskDetail 文件")
    dex(c, "ls /app/frontend/dist/assets/Tasks*.js 2>/dev/null | tail -3",
        label="B.4 最新 Tasks 文件")
    # 在最新 TaskDetail 文件里搜索关键字
    dex(c, "f=$(ls -t /app/frontend/dist/assets/TaskDetail*.js 2>/dev/null | head -1); echo FILE=$f; grep -oE '.{80}(setInterval|setTimeout|/api/v1/video/tasks|ws://|WebSocket|fetch|axios|useEffect|useState|onmessage|onclose).{200}' $f 2>/dev/null | head -40",
        label="B.5 最新 TaskDetail 内关键字上下文")
    # 入口 index 文件
    dex(c, "f=$(ls -t /app/frontend/dist/assets/index-*.js 2>/dev/null | head -1); echo FILE=$f; grep -oE '.{60}(setInterval|setTimeout|/api/v1/video/tasks|ws://|wss://|WebSocket|EventSource|axios|useQuery).{160}' $f 2>/dev/null | head -30",
        label="B.6 最新 index.js 内关键字上下文")
    # 全局搜索 task_id 轮询(可能反引号模板字符串)
    dex(c, "grep -oE 'tasks/\\$\\{[a-zA-Z_]+\\}|/api/v1/video/tasks/[^\"\\s\\\\]{1,40}' /app/frontend/dist/assets/*.js 2>/dev/null | head -20",
        label="B.7 任务 URL 模板")

    # ===== C. 后端 tasks.py 完整 WS 和 GET 任务接口 =====
    dex(c, "sed -n '1,210p' /app/app/api/tasks.py",
        label="C.1 /app/app/api/tasks.py 全文")

    # ===== D. 后端 task_service.py 完整 =====
    dex(c, "sed -n '1,230p' /app/app/services/task_service.py",
        label="D.1 /app/app/services/task_service.py 全文")

    # ===== E. 后端 worker.py 完整 =====
    dex(c, "sed -n '1,260p' /app/app/services/worker.py",
        label="E.1 /app/app/services/worker.py 全文")

    # ===== F. tracker_service.py 关键片段 =====
    # 找出 _annotate_frame 报 readonly numpy 那段
    dex(c, "sed -n '640,690p' /app/app/services/tracker_service.py",
        label="F.1 tracker_service.py _annotate_frame (640-690)")
    # 流重连逻辑
    dex(c, "sed -n '180,350p' /app/app/services/tracker_service.py",
        label="F.2 tracker_service.py 流重连段 (180-350)")

    # ===== G. main.py / supervisor 配置 =====
    dex(c, "cat /app/app/main.py",
        label="G.1 /app/app/main.py")
    dex(c, "cat /etc/supervisor/conf.d/p2.conf 2>/dev/null; cat /etc/supervisord.conf 2>/dev/null | head -60",
        label="G.2 supervisor 配置")

    # ===== H. torch / cuda 版本 =====
    dex(c, "python3 -c 'import torch; print(\"torch=\", torch.__version__); print(\"cuda=\", torch.version.cuda); print(\"avail=\", torch.cuda.is_available()); print(\"cap=\", torch.cuda.get_device_capability() if torch.cuda.is_available() else None)' 2>&1",
        label="H.1 容器内 torch/cuda 信息")
    # torch 安装信息
    dex(c, "pip show torch 2>/dev/null | head -10",
        label="H.2 pip show torch")

    # ===== I. uvicorn.log(不是 err log)关键片段 =====
    dex(c, "tail -80 /var/log/uvicorn.log 2>/dev/null",
        label="I.1 uvicorn.log 尾部(WS 推帧日志)")

    # ===== J. nginx 配置 =====
    dex(c, "cat /etc/nginx/conf.d/p2.conf 2>/dev/null; echo '---'; cat /etc/nginx/nginx.conf 2>/dev/null | head -60",
        label="J.1 nginx 配置")

    # ===== K. nvidia driver / 宿主 GPU arch =====
    run(c, "nvidia-smi --query-gpu=compute_cap,memory.total --format=csv",
        label="K.1 GPU compute capability")
    run(c, "nvidia-smi --query-gpu=driver_version --format=csv | tail -1",
        label="K.2 驱动版本")

    c.close()
    print("\n=== 第二轮调查完成 ===")


if __name__ == "__main__":
    main()
