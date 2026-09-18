import paramiko

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
    # jar 时间
    run(ssh, "ls -la /opt/sikong-cloud/sikong-cloud.jar")
    # jar 内是否有 识别流-ApiController 字符串
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | strings | grep '识别流-' | head -5")
    # jar 内是否有 识别流-外网构建
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/service/impl/MediaServerStreamProbeServiceImpl.class 2>&1 | strings | grep '识别流-' | head -5")
    # 后端启动日志（看是否正确加载）
    run(ssh, "echo szy666 | sudo -S head -5 /opt/sikong-cloud/logs/app.log 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
