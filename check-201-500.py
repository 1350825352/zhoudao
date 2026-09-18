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
    # 看后端最近日志，找 500/Exception
    run(ssh, "echo szy666 | sudo -S journalctl -u sikong-cloud -n 200 --no-pager 2>&1 | grep -iE 'error|exception|500' | tail -60")
    run(ssh, "echo szy666 | sudo -S journalctl -u sikong-cloud -n 200 --no-pager 2>&1 | tail -40")
    ssh.close()

if __name__ == "__main__":
    main()
