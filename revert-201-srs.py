import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)

    # 1. 恢复知眸 SRS 配置
    print("[1] 恢复知眸 SRS 配置 ...")
    run(ssh, "echo szy666 | sudo -S cp /opt/zhimou/srs.conf.bak /opt/zhimou/srs.conf 2>&1")
    run(ssh, "echo szy666 | sudo -S systemctl restart aivis_srs 2>&1")

    # 2. 重启 Docker Oryx
    print("\n[2] 重启 Docker Oryx ...")
    run(ssh, "echo szy666 | sudo -S docker update --restart=always oryx 2>&1")
    run(ssh, "echo szy666 | sudo -S docker start oryx 2>&1")

    # 3. 验证
    print("\n[3] 验证 ...")
    import time; time.sleep(5)
    run(ssh, "echo szy666 | sudo -S ss -tlnp | grep -E '1935|2022|11935|11985|18080' 2>&1")
    run(ssh, "echo szy666 | sudo -S systemctl status aivis_srs 2>&1 | head -5")
    run(ssh, "echo szy666 | sudo -S docker ps | grep oryx 2>&1")

    ssh.close()

if __name__ == "__main__":
    main()
