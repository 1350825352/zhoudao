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
    run(ssh, "ss -ltn 2>/dev/null | grep 6379")
    run(ssh, "echo szy666 | sudo -S grep -E '^bind|^protected-mode|^port|^requirepass' /etc/redis/redis.conf 2>/dev/null")
    run(ssh, "echo szy666 | sudo -S systemctl is-active redis-server redis 2>&1")
    # 测试本机连 redis
    run(ssh, "redis-cli -h 127.0.0.1 -p 6379 ping 2>&1")
    run(ssh, "redis-cli -h 192.168.2.201 -p 6379 ping 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
