import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=20):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 1. 先看知眸 SRS 完整配置
    run(ssh, "echo szy666 | sudo -S cat /opt/zhimou/srs.conf 2>&1")
    # 2. 看知眸 SRS 的启动方式（是否有 systemd 或 supervisor）
    run(ssh, "echo szy666 | sudo -S systemctl list-units | grep -i zhimou 2>&1")
    run(ssh, "ps aux | grep -i 'zhimou\\|srs' | grep -v grep")
    # 3. 看 Docker Oryx 是否可停
    run(ssh, "echo szy666 | sudo -S docker inspect oryx --format '{{.Name}} {{.State.Status}} {{.HostConfig.RestartPolicy.Name}}' 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
