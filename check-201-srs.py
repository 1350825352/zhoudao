import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=20):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
    stdout.channel.set_combine_stderr(True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # SRS 服务
    run(ssh, "echo szy666 | sudo -S systemctl status srs 2>&1 | head -20")
    # 端口
    run(ssh, "echo szy666 | sudo -S ss -tlnp | grep -E '1935|2022|8080|1985' 2>&1")
    # SRS 进程
    run(ssh, "ps aux | grep -i srs | grep -v grep")
    # SRS 配置
    run(ssh, "echo szy666 | sudo -S cat /opt/srs/conf/srs.conf 2>/dev/null | head -40")
    # SRS API 查看流列表
    run(ssh, "curl -s http://127.0.0.1:1985/api/v1/streams/ 2>&1 | head -20")
    # 看 SRS 日志最近报错
    run(ssh, "echo szy666 | sudo -S tail -30 /var/log/srs.log 2>/dev/null || echo szy666 | sudo -S journalctl -u srs -n 30 --no-pager 2>&1 | tail -30")
    ssh.close()

if __name__ == "__main__":
    main()
