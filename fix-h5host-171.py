#!/usr/bin/env python3
"""修改 171 的 h5.host 为外网地址 + 检查双网卡"""
import paramiko
import time

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"
DEPLOY_DIR = "/opt/sikong-cloud"

def main():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASS, timeout=15)

    # 1. 检查双网卡
    print("=== 171 双网卡情况 ===")
    stdin, stdout, stderr = client.exec_command("ip addr show | grep 'inet ' | grep -v 127.0.0.1")
    print(stdout.read().decode('utf-8', errors='replace'))

    # 2. 检查路由表
    print("=== 路由表 ===")
    stdin, stdout, stderr = client.exec_command("ip route show")
    print(stdout.read().decode('utf-8', errors='replace'))

    # 3. 修改 h5.host
    print("\n=== 修改 h5.host ===")
    sftp = client.open_sftp()
    config_path = f"{DEPLOY_DIR}/config/application-company-a.yml"

    with sftp.file(config_path, 'r') as f:
        content = f.read().decode('utf-8')

    # 查找 h5.host 配置
    old_val = "192.168.2.171"
    new_val = "223.113.158.186:50080"

    if "h5" in content and old_val in content:
        content = content.replace(old_val, new_val)
        with sftp.file(config_path, 'w') as f:
            f.write(content)
        print(f"h5.host: {old_val} → {new_val}")
    else:
        # 显示 h5 相关行
        for line in content.split('\n'):
            if 'h5' in line.lower() or 'host' in line.lower():
                print(f"  发现: {line.strip()}")
        print("尝试直接搜索 192.168.2.171...")
        if old_val in content:
            content = content.replace(old_val, new_val)
            with sftp.file(config_path, 'w') as f:
                f.write(content)
            print(f"已替换 {old_val} → {new_val}")
        else:
            print(f"未找到 {old_val}")

    sftp.close()

    # 4. 重启后端
    print("\n=== 重启后端 ===")
    stdin, stdout, stderr = client.exec_command(
        f"echo '{PASS}' | sudo -S systemctl restart sikong-cloud 2>&1"
    )
    stdout.read().decode('utf-8', errors='replace')

    print("等待 20 秒启动...")
    time.sleep(20)

    # 5. 验证
    stdin, stdout, stderr = client.exec_command(
        f"echo '{PASS}' | sudo -S systemctl is-active sikong-cloud 2>&1"
    )
    status = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"服务状态: {status}")

    stdin, stdout, stderr = client.exec_command("ss -tlnp | grep ':8080 '")
    port = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"8080 端口: {port if port else '未监听'}")

    # 6. 确认配置生效
    stdin, stdout, stderr = client.exec_command(
        f"grep -n 'h5' {DEPLOY_DIR}/config/application-company-a.yml"
    )
    grep_result = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"\nh5 配置行:\n{grep_result}")

    client.close()

if __name__ == "__main__":
    main()
