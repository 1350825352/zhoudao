#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""第三轮 v3:SFTP 拉取关键文件本地分析"""
import sys
import io
import re
import paramiko

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")
HOST, USER, PASS, CONTAINER = "192.168.2.208", "humh01", "1qaz2wsx", "p2-video"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASS, timeout=20)


def docker_cp(remote_path):
    """从容器拉文件到本地,返回 bytes"""
    # 用 docker exec + cat,通过 stdout 取
    cmd = f'docker exec {CONTAINER} cat {remote_path}'
    stdin, stdout, stderr = c.exec_command(cmd, timeout=120)
    data = stdout.read()
    return data


# 1. 拉取 _annotate_frame 段(700-800)
print("=" * 80)
print("[1] tracker_service.py _annotate_frame 段 (715-800)")
print("=" * 80)
cmd = f'docker exec {CONTAINER} sed -n "715,800p" /app/app/services/tracker_service.py'
stdin, stdout, stderr = c.exec_command(cmd, timeout=60)
print(stdout.read().decode("utf-8", errors="replace"))

# 2. 找 index.html 引用的入口 JS,以及它依赖的 TaskDetail 文件名
print("=" * 80)
print("[2] index.html 引用的入口 JS 文件")
print("=" * 80)
data = docker_cp("/app/frontend/dist/index.html")
print(data.decode("utf-8", errors="replace"))

# 入口文件名
m = re.search(r'src="(/assets/[^"]+)"', data.decode("utf-8", errors="replace"))
entry = m.group(1) if m else "/assets/index-DbiDmNzm.js"
print("入口:", entry)

# 3. 在入口 JS 里找 TaskDetail 引用
print("\n" + "=" * 80)
print(f"[3] 入口 JS {entry} 中 TaskDetail 引用")
print("=" * 80)
entry_data = docker_cp(entry).decode("utf-8", errors="replace")
print(f"入口文件大小: {len(entry_data)} 字节")
# 找形如 TaskDetail-XXX.js 的引用
matches = re.findall(r'TaskDetail-[A-Za-z0-9_\-]+\.js', entry_data)
print("入口引用的 TaskDetail 文件:", matches[:5])
matches2 = re.findall(r'Tasks-[A-Za-z0-9_\-]+\.js', entry_data)
print("入口引用的 Tasks 文件:", matches2[:5])

# 4. 拉取最新 TaskDetail 和 Tasks 文件,搜索 WS / 轮询逻辑
print("\n" + "=" * 80)
print("[4] 在所有 TaskDetail-*.js 里找关键字")
print("=" * 80)
cmd = f'docker exec {CONTAINER} sh -c "ls /app/frontend/dist/assets/TaskDetail-*.js"'
stdin, stdout, stderr = c.exec_command(cmd, timeout=30)
td_files = [l.strip() for l in stdout.read().decode().splitlines() if l.strip()]
print("找到 TaskDetail 文件数:", len(td_files))

# 找被入口引用的那个 TaskDetail
target_td = None
for f in td_files:
    fname = f.split("/")[-1]
    if fname in matches:
        target_td = f
        break
if target_td is None and td_files:
    target_td = td_files[0]  # 任选一个
print("分析 TaskDetail 文件:", target_td)

# 直接 cat 整个文件,本地 grep
if target_td:
    data = docker_cp(target_td).decode("utf-8", errors="replace")
    print(f"\n文件大小: {len(data)} 字节")

    # 找所有 setInterval/setTimeout 上下文
    print("\n--- setInterval/setTimeout 上下文 ---")
    for m in re.finditer(r'setInterval|setTimeout', data):
        s = max(0, m.start() - 150)
        e = min(len(data), m.end() + 250)
        print(f"\n[{m.start()}] ...{data[s:e]}...")

    # WebSocket 创建
    print("\n--- new WebSocket 上下文 ---")
    for m in re.finditer(r'new WebSocket|WebSocket\(|ws://|wss://', data):
        s = max(0, m.start() - 100)
        e = min(len(data), m.end() + 400)
        print(f"\n[{m.start()}] ...{data[s:e]}...")

    # onmessage / onclose / onerror
    print("\n--- onmessage/onclose/onerror 上下文 ---")
    for kw in ['onmessage', 'onclose', 'onerror', 'readyState', 'drawImage']:
        for m in re.finditer(kw, data):
            s = max(0, m.start() - 80)
            e = min(len(data), m.end() + 300)
            print(f"\n[{kw} @ {m.start()}] ...{data[s:e]}...")

    # /api/v1/video/tasks 调用
    print("\n--- /api/v1/video/tasks 调用上下文 ---")
    for m in re.finditer(r'/api/v1/video/tasks', data):
        s = max(0, m.start() - 100)
        e = min(len(data), m.end() + 300)
        print(f"\n[{m.start()}] ...{data[s:e]}...")

# 5. uvicorn 重启统计
print("\n" + "=" * 80)
print("[5] uvicorn 重启统计")
print("=" * 80)
cmd = f'''docker exec {CONTAINER} sh -c "echo -n 'spawn: '; grep -c \\"spawned: 'uvicorn'\\" /var/log/supervisord.log; echo -n 'SIGSEGV: '; grep -c SIGSEGV /var/log/supervisord.log; echo -n 'SIGKILL: '; grep -c SIGKILL /var/log/supervisord.log"'''
stdin, stdout, stderr = c.exec_command(cmd, timeout=30)
print(stdout.read().decode("utf-8", errors="replace"))

# 6. ultralytics / opencv 版本
print("\n" + "=" * 80)
print("[6] ultralytics / opencv / torch 版本")
print("=" * 80)
cmd = f'docker exec {CONTAINER} sh -c "pip list 2>/dev/null | grep -iE \\"opencv|ultralytics|torch|numpy\\""'
stdin, stdout, stderr = c.exec_command(cmd, timeout=30)
print(stdout.read().decode("utf-8", errors="replace"))

c.close()
print("\n=== 完成 ===")
