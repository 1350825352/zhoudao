#!/usr/bin/env python3
"""171 ARM 机器环境探测 + SSH 免密配置"""
import paramiko
import os
import sys

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"
PORT = 22

def ssh_exec(hostname, username, password, commands, port=22):
    """SSH 执行命令并返回输出"""
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(hostname, port=port, username=username, password=password, timeout=10)
        results = []
        for cmd in commands:
            stdin, stdout, stderr = client.exec_command(cmd, timeout=30)
            out = stdout.read().decode('utf-8', errors='replace').strip()
            err = stderr.read().decode('utf-8', errors='replace').strip()
            results.append((cmd, out, err))
        return results
    finally:
        client.close()

def setup_ssh_key(hostname, username, password, port=22):
    """配置 SSH 免密登录"""
    # 1. 检查本地是否有 SSH 密钥
    home = os.path.expanduser("~")
    ssh_dir = os.path.join(home, ".ssh")
    pub_key_path = os.path.join(ssh_dir, "id_rsa.pub")
    
    if not os.path.exists(pub_key_path):
        print("[SSH] 本地没有 SSH 密钥，生成中...")
        os.system(f'ssh-keygen -t rsa -b 2048 -f "{os.path.join(ssh_dir, "id_rsa")}" -N "" -q')
        print("[SSH] 密钥生成完成")
    
    # 2. 读取公钥
    with open(pub_key_path, 'r') as f:
        pub_key = f.read().strip()
    
    # 3. 把公钥写到远程机器
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(hostname, port=port, username=username, password=password, timeout=10)
        
        # 创建 .ssh 目录
        client.exec_command('mkdir -p ~/.ssh && chmod 700 ~/.ssh')
        
        # 检查是否已存在
        stdin, stdout, stderr = client.exec_command('cat ~/.ssh/authorized_keys 2>/dev/null')
        existing = stdout.read().decode('utf-8', errors='replace')
        
        if pub_key in existing:
            print("[SSH] 公钥已存在，无需重复添加")
        else:
            # 追加公钥
            cmd = f'echo "{pub_key}" >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys'
            stdin, stdout, stderr = client.exec_command(cmd)
            err = stderr.read().decode('utf-8', errors='replace').strip()
            if err:
                print(f"[SSH] 添加公钥警告: {err}")
            else:
                print("[SSH] 公钥添加成功")
        
        client.close()
        return True
    except Exception as e:
        print(f"[SSH] 配置免密失败: {e}")
        return False

def probe_environment():
    """探测 171 机器环境"""
    print("\n" + "="*60)
    print(f"探测 {HOST} 环境信息")
    print("="*60)
    
    commands = [
        ("uname -a", "系统架构"),
        ("cat /etc/os-release | head -5", "OS 版本"),
        ("free -h", "内存"),
        ("df -h /", "磁盘(根分区)"),
        ("nproc", "CPU核数"),
        ("java -version 2>&1", "Java"),
        ("node --version 2>/dev/null || echo '未安装'", "Node.js"),
        ("nginx -v 2>&1 || echo '未安装'", "Nginx"),
        ("docker --version 2>/dev/null || echo '未安装'", "Docker"),
        ("mysql --version 2>/dev/null || echo '未安装'", "MySQL"),
        ("redis-server --version 2>/dev/null || echo '未安装'", "Redis"),
        ("ip addr show | grep 'inet ' | grep -v 127.0.0.1", "IP地址"),
        ("cat /proc/cpuinfo | grep 'model name' | head -1", "CPU型号"),
        ("ls /opt/ 2>/dev/null", "/opt目录"),
    ]
    
    results = ssh_exec(HOST, USER, PASS, [c[0] for c in commands])
    
    for (cmd, label), (_, out, err) in zip(commands, results):
        print(f"\n--- {label} ---")
        output = out if out else err
        print(output if output else "(无输出)")

def main():
    print("="*60)
    print(f"连接 {HOST}:{PORT} 用户={USER}")
    print("="*60)
    
    # 1. 测试 SSH 连接
    try:
        results = ssh_exec(HOST, USER, PASS, ["echo 'SSH连接成功'"])
        print(results[0][1])
    except Exception as e:
        print(f"SSH 连接失败: {e}")
        sys.exit(1)
    
    # 2. 探测环境
    probe_environment()
    
    print("\n" + "="*60)
    print("探测完成")
    print("="*60)

if __name__ == "__main__":
    main()
