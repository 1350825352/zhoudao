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
    # 用 grep -a 检查 class 文件中的字符串（二进制模式）
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | grep -a 'internal=' | head -3")
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/service/impl/MediaServerStreamProbeServiceImpl.class 2>&1 | grep -a 'tpl=' | head -3")
    # 看后端日志末尾30行（找 System.out 输出）
    run(ssh, "echo szy666 | sudo -S tail -30 /opt/sikong-cloud/logs/app.log 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
