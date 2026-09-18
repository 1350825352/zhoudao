#!/usr/bin/env python3
"""修复 201 服务: User 改成 szy01 + Redis 前缀 + 日志目录权限"""
import paramiko
import time

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.201', username='szy01', password='szy666', timeout=10)

# 1. 修复 systemd User=nvidia -> User=szy01
print("=== 1. 修复 systemd User ===")
cmd = "echo 'szy666' | sudo -S sed -i 's/User=nvidia/User=szy01/' /etc/systemd/system/sikong-cloud.service 2>&1"
stdin, stdout, stderr = c.exec_command(cmd)
print(stdout.read().decode('utf-8', errors='replace').strip())

# 确认
stdin, stdout, stderr = c.exec_command("grep 'User=' /etc/systemd/system/sikong-cloud.service")
print("确认: " + stdout.read().decode('utf-8', errors='replace').strip())

# 2. 修复日志目录权限
print("\n=== 2. 修复日志目录权限 ===")
stdin, stdout, stderr = c.exec_command("echo 'szy666' | sudo -S chown -R szy01:szy01 /opt/sikong-cloud/logs/ /opt/sikong-cloud/zhimou-images/ /opt/sikong-cloud/zhimou-minio-temp/ 2>&1")
print(stdout.read().decode('utf-8', errors='replace').strip() or "done")

# 3. 修复 Redis 前缀 spring.data.redis -> spring.redis
print("\n=== 3. 修复 Redis 前缀 ===")
stdin, stdout, stderr = c.exec_command("echo 'szy666' | sudo -S sed -i 's/spring.data.redis/spring.redis/g' /opt/sikong-cloud/config/application-company-a.yml 2>&1")
print(stdout.read().decode('utf-8', errors='replace').strip() or "done")
stdin, stdout, stderr = c.exec_command("grep 'redis' /opt/sikong-cloud/config/application-company-a.yml")
print("确认: " + stdout.read().decode('utf-8', errors='replace').strip())

# 4. 确认 callback-url
print("\n=== 4. callback-url 确认 ===")
stdin, stdout, stderr = c.exec_command("grep 'callback' /opt/sikong-cloud/config/application-company-a.yml")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 5. 重载并重启
print("\n=== 5. 重启服务 ===")
stdin, stdout, stderr = c.exec_command("echo 'szy666' | sudo -S systemctl daemon-reload 2>&1")
print(stdout.read().decode('utf-8', errors='replace').strip() or "daemon-reload done")
stdin, stdout, stderr = c.exec_command("echo 'szy666' | sudo -S systemctl restart sikong-cloud 2>&1")
print(stdout.read().decode('utf-8', errors='replace').strip() or "restart done")

# 6. 等待 25 秒
print("\n等待 25 秒...")
time.sleep(25)

# 7. 检查状态
print("\n=== 6. 服务状态 ===")
stdin, stdout, stderr = c.exec_command("systemctl is-active sikong-cloud")
status = stdout.read().decode('utf-8', errors='replace').strip()
print(f"sikong-cloud: {status}")

stdin, stdout, stderr = c.exec_command("ss -tlnp | grep ':8080 ' || echo '8080 未监听'")
print("8080: " + stdout.read().decode('utf-8', errors='replace').strip())

stdin, stdout, stderr = c.exec_command("ps aux | grep java | grep -v grep | head -1")
print("Java: " + stdout.read().decode('utf-8', errors='replace').strip())

# 8. 查日志
print("\n=== 7. 后端日志 ===")
stdin, stdout, stderr = c.exec_command("tail -20 /opt/sikong-cloud/logs/app.log 2>/dev/null || echo '日志文件不存在'")
print(stdout.read().decode('utf-8', errors='replace').strip())

c.close()
