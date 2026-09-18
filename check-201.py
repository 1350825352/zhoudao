#!/usr/bin/env python3
"""查 201 部署状态"""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.201', username='szy01', password='szy666', timeout=10)

# 1. 服务状态
print("=== 1. 服务状态 ===")
stdin, stdout, stderr = c.exec_command("systemctl is-active sikong-cloud 2>/dev/null; systemctl is-active nginx 2>/dev/null; systemctl is-active redis-server 2>/dev/null")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 2. 端口监听
print("\n=== 2. 端口监听 ===")
stdin, stdout, stderr = c.exec_command("ss -tlnp 2>/dev/null | grep -E '8080|80|6379' || echo '无端口监听'")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 3. Java 进程
print("\n=== 3. Java 进程 ===")
stdin, stdout, stderr = c.exec_command("ps aux | grep java | grep -v grep || echo '无 Java 进程'")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 4. 后端日志最后 30 行
print("\n=== 4. 后端日志 (最后 30 行) ===")
stdin, stdout, stderr = c.exec_command("tail -30 /opt/sikong-cloud/logs/app.log 2>/dev/null || echo '日志文件不存在'")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 5. 配置文件检查
print("\n=== 5. 配置文件 ===")
stdin, stdout, stderr = c.exec_command("cat /opt/sikong-cloud/config/application-company-a.yml 2>/dev/null | head -30 || echo '配置文件不存在'")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 6. 前端文件
print("\n=== 6. 前端文件 ===")
stdin, stdout, stderr = c.exec_command("ls -la /opt/sikong-cloud/ 2>/dev/null || echo '目录不存在'")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 7. Nginx 配置
print("\n=== 7. Nginx 配置 ===")
stdin, stdout, stderr = c.exec_command("cat /etc/nginx/sites-enabled/sikong-cloud 2>/dev/null || cat /etc/nginx/conf.d/sikong-cloud.conf 2>/dev/null || echo 'Nginx 配置不存在'")
print(stdout.read().decode('utf-8', errors='replace').strip()[:2000])

# 8. JDK 版本
print("\n=== 8. JDK 版本 ===")
stdin, stdout, stderr = c.exec_command("java -version 2>&1")
print(stdout.read().decode('utf-8', errors='replace').strip())

# 9. systemd 服务文件
print("\n=== 9. systemd 服务文件 ===")
stdin, stdout, stderr = c.exec_command("cat /etc/systemd/system/sikong-cloud.service 2>/dev/null || echo '服务文件不存在'")
print(stdout.read().decode('utf-8', errors='replace').strip())

c.close()
