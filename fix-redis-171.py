#!/usr/bin/env python3
"""修复 171 上的 Redis 配置"""
import paramiko

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"
DEPLOY_DIR = "/opt/sikong-cloud"

def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASS, timeout=15)
    
    print("[修复] 修改 Redis 配置 spring.data.redis → spring.redis...")
    
    # 读取配置文件
    sftp = client.open_sftp()
    config_path = f"{DEPLOY_DIR}/config/application-base.yml"
    
    with sftp.file(config_path, 'r') as f:
        content = f.read().decode('utf-8')
    
    # spring.data.redis 改成 spring.redis (在 application-base.yml 里)
    # 因为 Spring Boot 2.7 用的是 spring.redis.* 不是 spring.data.redis.*
    old = """  data:
    redis:
      timeout: 3000
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4"""
    
    new = """  redis:
      timeout: 3000
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 4"""
    
    if old in content:
        content = content.replace(old, new)
        with sftp.file(config_path, 'w') as f:
            f.write(content)
        print("[修复] application-base.yml 已修改")
    else:
        print("[修复] 未找到匹配的配置段，尝试直接替换...")
        # 简单替换
        content = content.replace("  data:\n    redis:", "  redis:")
        with sftp.file(config_path, 'w') as f:
            f.write(content)
        print("[修复] 已替换")
    
    # 同时修改 application-company-a.yml
    company_path = f"{DEPLOY_DIR}/config/application-company-a.yml"
    with sftp.file(company_path, 'r') as f:
        content2 = f.read().decode('utf-8')
    
    # spring.data.redis → spring.redis
    content2 = content2.replace("  data:\n    redis:", "  redis:")
    
    with sftp.file(company_path, 'w') as f:
        f.write(content2)
    print("[修复] application-company-a.yml 已修改")
    
    sftp.close()
    
    # 重启后端
    print("[修复] 重启后端...")
    stdin, stdout, stderr = client.exec_command(
        f"echo '{PASS}' | sudo -S systemctl restart sikong-cloud 2>&1"
    )
    stdout.read().decode('utf-8', errors='replace')
    
    import time
    print("[修复] 等待 20 秒启动...")
    time.sleep(20)
    
    # 检查状态
    stdin, stdout, stderr = client.exec_command(
        f"echo '{PASS}' | sudo -S systemctl is-active sikong-cloud 2>&1"
    )
    status = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"[修复] 服务状态: {status}")
    
    # 检查日志
    stdin, stdout, stderr = client.exec_command(
        f"tail -30 {DEPLOY_DIR}/logs/app.log 2>/dev/null"
    )
    log = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"[修复] 启动日志:\n{log}")
    
    # 检查端口
    stdin, stdout, stderr = client.exec_command("ss -tlnp | grep ':8080 '")
    port = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"\n[修复] 8080 端口: {port if port else '未监听'}")
    
    client.close()

if __name__ == "__main__":
    main()
