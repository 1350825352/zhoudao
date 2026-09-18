import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== {c}")
        if out.strip(): print(out)
    except Exception as e:
        print(f"\n=== {c}\n  TIMEOUT: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    time.sleep(3)
    # 查识别流调试日志
    run(ssh, "echo szy666 | sudo -S grep '识别流-' /opt/sikong-cloud/logs/app.log 2>&1 | tail -10")
    # 查实时识别请求和后续日志
    run(ssh, "echo szy666 | sudo -S grep -A2 '实时识别请求' /opt/sikong-cloud/logs/app.log 2>&1 | tail -20")
    # live_stream 状态
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,status,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 1;' 2>&1 | grep -v Warning")
    ssh.close()

if __name__ == "__main__":
    main()
