import paramiko, time, requests

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
        stdout.channel.set_combine_stderr(True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        print(out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT/ERR: {e}")

def main():
    # 等 15 秒让 Spring Boot 起来
    print("等待 Spring Boot 启动 15 秒...")
    time.sleep(15)

    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    run(ssh, "systemctl is-active sikong-cloud")
    run(ssh, "ss -ltn 2>/dev/null | grep -E '8080|6379|3306' | head")
    # 查后端启动日志（journalctl 最近 80 行）
    run(ssh, "echo szy666 | sudo -S journalctl -u sikong-cloud -n 80 --no-pager 2>&1 | tail -60")
    ssh.close()

    # 测试 HTTP 健康
    try:
        r = requests.get("http://192.168.2.201:8080/api/wayline/list", timeout=5)
        print(f"\n[HTTP] /api/wayline/list -> {r.status_code}")
        print(r.text[:300])
    except Exception as e:
        print(f"\n[HTTP] 失败: {e}")

if __name__ == "__main__":
    main()
