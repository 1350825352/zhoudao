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
    # 检查 jar 内 ApiController.class 是否包含 probeUrl
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | strings | grep -i 'probeUrl' | head -5")
    # 检查 MediaServerStreamProbeServiceImpl.class 是否包含 pullUrlTemplateAi
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/service/impl/MediaServerStreamProbeServiceImpl.class 2>&1 | strings | grep -i 'pullUrlTemplateAi\\|pull-url-template-ai' | head -5")
    # 模拟外网请求（用外网IP访问后端）
    run(ssh, "curl -s 'http://223.113.158.186:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    # 模拟内网请求
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
