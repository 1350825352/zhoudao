import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
    stdout.channel.set_combine_stderr(True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 看最近的 ERROR
    run(ssh, "echo szy666 | sudo -S grep -iE 'error|exception|doesn.t exist' /opt/sikong-cloud/logs/app.log 2>&1 | tail -30")
    # 看现有的表
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'show tables;' 2>&1 | grep -i switch")
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'show tables;' 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
