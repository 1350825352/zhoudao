#!/usr/bin/env python3
"""查 11:58 后的飞行数据链路"""
import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.2.171', username='nvidia', password='nvidiaszyzkytj', timeout=10)

# 1. 11:58 后的上报图片
print("=== 11:58 后图片上报 ===")
stdin, stdout, stderr = c.exec_command("grep '11:5[89]\\|12:0[0-9]' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep -i '上报图片\\|reportImage\\|批量上报' | head -20")
print(stdout.read().decode('utf-8', errors='replace').strip() or "无")

# 2. 11:58 后的知眸回调
print("\n=== 11:58 后知眸回调 ===")
stdin, stdout, stderr = c.exec_command("grep '11:5[89]\\|12:0[0-9]' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep -i '图片回调\\|callback' | head -20")
print(stdout.read().decode('utf-8', errors='replace').strip() or "无")

# 3. 11:58 后 MinIO 批处理
print("\n=== 11:58 后 MinIO ===")
stdin, stdout, stderr = c.exec_command("grep '11:5[89]\\|12:0[0-9]' /opt/sikong-cloud/logs/app.log 2>/dev/null | grep -i 'minio\\|MinIO' | head -10")
print(stdout.read().decode('utf-8', errors='replace').strip() or "无")

# 4. 查 API 验证数据
print("\n=== API 查最新数据 ===")
import requests
r = requests.get("http://192.168.2.171/api/zhimou/image-results/page", params={"page":1,"size":3}, timeout=5)
j = r.json()
print(f"total={j.get('data',{}).get('total')}")
for rec in j.get('data',{}).get('list',[])[:3]:
    print(f"  id={rec.get('id')} time={rec.get('event_time')} app={rec.get('rule_name')}")

c.close()
