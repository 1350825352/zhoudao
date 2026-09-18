#!/usr/bin/env python3
"""查 171 上知眸 callback-url 配置"""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.171', username='nvidia', password='nvidiaszyzkytj', timeout=10)

for f in ['application-company-a.yml', 'application-base.yml']:
    path = f'/opt/sikong-cloud/config/{f}'
    print(f"=== {f} ===")
    stdin, stdout, stderr = c.exec_command(f'grep -n "callback\\|report\\|openapi\\|h5" {path} 2>/dev/null')
    out = stdout.read().decode('utf-8', errors='replace').strip()
    print(out if out else "(无匹配)")

# 也查数据库 zhimou_task_config 表
print("\n=== 数据库 zhimou_task_config 表 ===")
stdin, stdout, stderr = c.exec_command(
    "mysql -h192.168.2.200 -uroot -pCkw123456! -Dsikong_cloud -e 'SELECT id, report_url FROM zhimou_task_config LIMIT 5' 2>/dev/null"
)
out = stdout.read().decode('utf-8', errors='replace').strip()
print(out if out else "无数据库或无数据")

c.close()
