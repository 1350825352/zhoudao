import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 直接看最近30行原始日志
    run(ssh, "echo szy666 | sudo -S tail -30 /opt/sikong-cloud/logs/app.log 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
