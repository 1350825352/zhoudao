#!/usr/bin/env python3
"""查 201 同步航线/项目 500 错误"""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.201', username='szy01', password='szy666', timeout=10)

# 1. 查 500 错误
print("=== 1. 500 错误日志 ===")
stdin, stdout, stderr = c.exec_command("grep -i '500\\|error\\|exception\\|航线\\|wayline\\|项目同步\\|project.*sync\\|sync' /opt/sikong-cloud/logs/app.log 2>/dev/null | tail -30")
print(stdout.read().decode('utf-8', errors='replace').strip() or "无匹配")

# 2. 查最近 ERROR 级别日志
print("\n=== 2. ERROR 级别日志 ===")
stdin, stdout, stderr = c.exec_command("grep 'ERROR' /opt/sikong-cloud/logs/app.log 2>/dev/null | tail -20")
print(stdout.read().decode('utf-8', errors='replace').strip() or "无 ERROR")

# 3. 查 OpenAPI 调用日志
print("\n=== 3. OpenAPI 调用日志 ===")
stdin, stdout, stderr = c.exec_command("grep -i 'openapi\\|DJI.*API\\|token\\|401\\|403' /opt/sikong-cloud/logs/app.log 2>/dev/null | tail -20")
print(stdout.read().decode('utf-8', errors='replace').strip() or "无匹配")

# 4. 最新 50 行日志
print("\n=== 4. 最新 50 行日志 ===")
stdin, stdout, stderr = c.exec_command("tail -50 /opt/sikong-cloud/logs/app.log 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace').strip())

c.close()
