import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"
LOCAL_JAR = "target/sikong-cloud-api-1.0.0.jar"

def run(ssh, c, timeout=30):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"$ {c}")
        if out.strip(): print(out)
    except Exception as e:
        print(f"$ {c}\n  TIMEOUT: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 上传 jar
    sftp = paramiko.SFTPClient.from_transport(ssh.get_transport())
    print("上传中...")
    sftp.put(LOCAL_JAR, '/tmp/sikong-cloud.jar')
    print("上传完成")
    sftp.close()
    # 停止 → 复制 → 启动
    run(ssh, "echo szy666 | sudo -S systemctl stop sikong-cloud 2>&1")
    run(ssh, "echo szy666 | sudo -S cp /tmp/sikong-cloud.jar /opt/sikong-cloud/sikong-cloud.jar 2>&1")
    run(ssh, "echo szy666 | sudo -S systemctl start sikong-cloud 2>&1")
    time.sleep(8)
    run(ssh, "echo szy666 | sudo -S systemctl is-active sikong-cloud 2>&1")
    run(ssh, "echo szy666 | sudo -S tail -5 /opt/sikong-cloud/logs/app.log 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
