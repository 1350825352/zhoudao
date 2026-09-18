import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
        stdout.channel.set_combine_stderr(True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        print(out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT/ERR: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    run(ssh, "echo szy666 | sudo -S grep -iE 'error|exception|caused by' /opt/sikong-cloud/logs/app.log 2>&1 | tail -80")
    run(ssh, "echo szy666 | sudo -S tail -100 /opt/sikong-cloud/logs/app.log 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
