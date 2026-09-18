import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=10):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        if out.strip(): print(out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 标记一下当前时间
    run(ssh, "date '+%Y-%m-%d %H:%M:%S'")
    # 调用识别视频接口 5 次
    for i in range(5):
        run(ssh, f"curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1 | python3 -c 'import sys,json; d=json.load(sys.stdin); print(f\"available={{d[\\\"data\\\"][\\\"available\\\"]}} url={{d[\\\"data\\\"][\\\"url\\\"][:80]}}\")'")
        time.sleep(1)
    # 看最新日志
    run(ssh, "echo szy666 | sudo -S grep -A2 '实时识别' /opt/sikong-cloud/logs/app.log 2>&1 | tail -30")
    # 看流探测 DEBUG 日志（包括识别流）
    run(ssh, "echo szy666 | sudo -S grep '流探测-DEBUG.*ai\\|流探测-DEBUG.*live/ai\\|识别流.*稳定\\|识别流.*失败' /opt/sikong-cloud/logs/app.log 2>&1 | tail -20")
    ssh.close()

if __name__ == "__main__":
    main()
