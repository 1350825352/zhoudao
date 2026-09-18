#!/usr/bin/env python3
"""重启 201 后端并检查 OpenAPI 配置"""
import paramiko
import time

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.201', username='szy01', password='szy666', timeout=10)

# 1. 查看当前 OpenAPI 配置
print("=== 1. OpenAPI 配置 ===")
stdin, stdout, stderr = c.exec_command("grep -A5 'openapi' /opt/sikong-cloud/config/application-company-a.yml 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 2. 查看完整配置
print("\n=== 2. 完整配置 ===")
stdin, stdout, stderr = c.exec_command("cat /opt/sikong-cloud/config/application-company-a.yml 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 3. 重启后端
print("\n=== 3. 重启后端 ===")
stdin, stdout, stderr = c.exec_command("echo 'szy666' | sudo -S systemctl restart sikong-cloud 2>&1")
print(stdout.read().decode('utf-8', errors='replace').strip() or "restart done")

print("等待 25 秒...")
time.sleep(25)

# 4. 检查状态
print("\n=== 4. 服务状态 ===")
stdin, stdout, stderr = c.exec_command("systemctl is-active sikong-cloud")
print("sikong-cloud: " + stdout.read().decode('utf-8', errors='replace').strip())

stdin, stdout, stderr = c.exec_command("ss -tlnp | grep ':8080 ' || echo '8080 未监听'")
print("8080: " + stdout.read().decode('utf-8', errors='replace').strip())

# 5. 查看启动日志
print("\n=== 5. 启动日志 ===")
stdin, stdout, stderr = c.exec_command("tail -40 /opt/sikong-cloud/logs/app.log 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace').strip())

c.close()
