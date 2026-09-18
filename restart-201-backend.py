import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"$ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    run(ssh, "echo szy666 | sudo -S cp /home/szy01/sikong-cloud.jar /opt/sikong-cloud/sikong-cloud.jar 2>&1")
    run(ssh, "echo szy666 | sudo -S systemctl restart sikong-cloud 2>&1")
    import time; time.sleep(5)
    run(ssh, "echo szy666 | sudo -S systemctl is-active sikong-cloud 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
