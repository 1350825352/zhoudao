import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 检查 Java 进程
    run(ssh, "ps aux | grep java | grep -v grep")
    # 强制重启
    run(ssh, "echo szy666 | sudo -S systemctl stop sikong-cloud 2>&1")
    run(ssh, "echo szy666 | sudo -S pkill -9 -f 'sikong-cloud' 2>&1")
    time.sleep(3)
    run(ssh, "ps aux | grep java | grep -v grep")
    # 启动
    run(ssh, "echo szy666 | sudo -S systemctl start sikong-cloud 2>&1")
    time.sleep(10)
    run(ssh, "echo szy666 | sudo -S systemctl is-active sikong-cloud 2>&1")
    # 调 API
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    # 看最新日志
    run(ssh, "echo szy666 | sudo -S tail -10 /opt/sikong-cloud/logs/app.log 2>&1 | grep -E '可播放|识别流'")
    ssh.close()

if __name__ == "__main__":
    main()
