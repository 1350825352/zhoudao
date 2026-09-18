import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=20):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
        stdout.channel.set_combine_stderr(True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        print(out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT/ERR: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 1. 备份 + 修改 bind 与 protected-mode
    run(ssh, "echo szy666 | sudo -S cp /etc/redis/redis.conf /etc/redis/redis.conf.bak.$(date +%s) 2>&1")
    run(ssh, "echo szy666 | sudo -S sed -i 's/^bind 127.0.0.1 ::1/bind 0.0.0.0/' /etc/redis/redis.conf 2>&1")
    run(ssh, "echo szy666 | sudo -S sed -i 's/^protected-mode yes/protected-mode no/' /etc/redis/redis.conf 2>&1")
    run(ssh, "echo szy666 | sudo -S grep -E '^bind|^protected-mode|^port' /etc/redis/redis.conf 2>&1")
    # 2. 重启 redis
    run(ssh, "echo szy666 | sudo -S systemctl restart redis-server 2>&1; sleep 2; echo szy666 | sudo -S systemctl is-active redis-server 2>&1")
    run(ssh, "ss -ltn 2>/dev/null | grep 6379")
    # 3. 验证
    run(ssh, "redis-cli -h 192.168.2.201 -p 6379 ping 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
