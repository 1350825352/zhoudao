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
    time.sleep(5)
    # 调一次识别视频接口
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1 | head -3")
    time.sleep(2)
    # 看识别流调试日志
    run(ssh, "echo szy666 | sudo -S grep '识别流-' /opt/sikong-cloud/logs/app.log 2>&1 | tail -10")
    # 看最近的实时识别请求
    run(ssh, "echo szy666 | sudo -S grep '实时识别请求' /opt/sikong-cloud/logs/app.log 2>&1 | tail -5")
    # live_stream
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 1;' 2>&1 | grep -v Warning")
    ssh.close()

if __name__ == "__main__":
    main()
