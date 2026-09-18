#!/usr/bin/env python3
"""修复 171 的知眸 callback-url"""
import paramiko
import time

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, username=USER, password=PASS, timeout=10)

# 1. 查看当前配置
path = "/opt/sikong-cloud/config/application-company-a.yml"
sftp = c.open_sftp()
with sftp.file(path, 'r') as f:
    content = f.read().decode('utf-8')

# 2. 找到 callback-url 行，显示上下文
for i, line in enumerate(content.split('\n'), 1):
    if 'callback' in line.lower() or 'h5' in line.lower():
        print(f"  L{i}: {line}")

# 3. 修复: 用内网直连地址(知眸服务器 202 和 171 都在内网)
old_url = "http://223.113.158.186:50080:8080/api/zhimou/callback"
new_url = "http://192.168.2.171:8080/api/zhimou/callback"

if old_url in content:
    content = content.replace(old_url, new_url)
    with sftp.file(path, 'w') as f:
        f.write(content)
    print(f"\n已修复: {old_url}")
    print(f"     → {new_url}")
elif "callback-url: http://223.113.158.186:50080" in content:
    # 可能是变量拼接形式
    content = content.replace(
        "callback-url: http://${h5.host}:${server.port}/api/zhimou/callback",
        f"callback-url: {new_url}"
    ).replace(
        "callback-url: http://${h5.host}:8080/api/zhimou/callback",
        f"callback-url: {new_url}"
    )
    with sftp.file(path, 'w') as f:
        f.write(content)
    print(f"\n已修复(变量形式)")

# 4. 确认修复后
with sftp.file(path, 'r') as f:
    new_content = f.read().decode('utf-8')
for i, line in enumerate(new_content.split('\n'), 1):
    if 'callback' in line.lower():
        print(f"  修复后 L{i}: {line}")

sftp.close()

# 5. 重启后端
print("\n重启后端...")
stdin, stdout, stderr = c.exec_command(
    f"echo '{PASS}' | sudo -S systemctl restart sikong-cloud 2>&1"
)
stdout.read()
print("等待 20 秒...")
time.sleep(20)

# 6. 验证
stdin, stdout, stderr = c.exec_command(
    f"echo '{PASS}' | sudo -S systemctl is-active sikong-cloud 2>&1"
)
status = stdout.read().decode('utf-8', errors='replace').strip()
print(f"服务状态: {status}")

stdin, stdout, stderr = c.exec_command("ss -tlnp | grep ':8080 '")
port = stdout.read().decode('utf-8', errors='replace').strip()
print(f"8080: {port if port else '未监听'}")

c.close()
