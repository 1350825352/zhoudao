#!/usr/bin/env python3
"""查 171 最新日志 - 知眸回调和入库"""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.171', username='nvidia', password='nvidiaszyzkytj', timeout=10)

# 1. 最新 30 行日志
print("=== 最新 30 行日志 ===")
stdin, stdout, stderr = c.exec_command("tail -30 /opt/sikong-cloud/logs/app.log 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace'))

# 2. 查 12:00 后的知眸回调
print("\n=== 12:00 后知眸回调 ===")
stdin, stdout, stderr = c.exec_command("grep '12:[0-5][0-9]' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep -i 'callback\\|知眸.*回调\\|入库\\|清洗\\|COMPLETED\\|上报图片' | head -20")
out = stdout.read().decode('utf-8', errors='replace').strip()
print(out if out else "12:00 后无知眸回调日志")

# 3. 查 report_url 配置是否生效
print("\n=== 当前 callback-url 配置 ===")
stdin, stdout, stderr = c.exec_command("grep 'callback' /opt/sikong-cloud/config/application-company-a.yml 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace'))

# 4. 新创建任务的 report_url
print("\n=== 最近的 report_url ===")
stdin, stdout, stderr = c.exec_command("grep 'report_url' /opt/sikong-cloud/logs/app.log 2>/dev/null | tail -5")
print(stdout.read().decode('utf-8', errors='replace'))

c.close()
