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
    # 从 jar 提取 class 到临时文件，用 javap 反编译
    run(ssh, "cd /tmp && echo szy666 | sudo -S unzip -o /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 && javap -p -constants BOOT-INF/classes/com/dji/sikong/controller/ApiController.class 2>&1 | grep STABLE")
    ssh.close()

if __name__ == "__main__":
    main()
