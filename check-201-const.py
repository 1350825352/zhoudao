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
    # 检查 class 文件中的常量值
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | grep -a 'STABLE_PROBE' | head -3")
    # 检查 class 中 STABLE_PROBE_THRESHOLD 的值（搜索 1/1 或 1/3）
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | xxd | grep -A1 'PROBE' | head -5")
    ssh.close()

if __name__ == "__main__":
    main()
