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
    except:
        pass

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 调 API
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    # 检查 jar 内是否有 returnUrl
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | grep -a 'returnUrl' | head -3")
    # 检查最近 50 行日志
    run(ssh, "echo szy666 | sudo -S tail -50 /opt/sikong-cloud/logs/app.log 2>&1 | grep -E '识别流|实时识别|returnUrl|probeUrl'")
    ssh.close()

if __name__ == "__main__":
    main()
