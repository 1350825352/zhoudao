import paramiko, sys

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
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
    run(ssh, "cat /home/szy01/.my.cnf 2>/dev/null || echo 'no ~/.my.cnf'")
    run(ssh, "ls /root/.my.cnf /root/mysql_root_password.txt /root/.mysql_root_password 2>/dev/null")
    run(ssh, "echo szy666 | sudo -S head -5 /root/.my.cnf 2>/dev/null; echo szy666 | sudo -S cat /root/mysql_root_password.txt 2>/dev/null")
    run(ssh, "mysql -e 'show databases;' 2>&1 | head -20")
    run(ssh, "mysql -uroot -e 'show databases;' 2>&1 | head -20")
    run(ssh, "ls -la /home/szy01/ | head -20")
    ssh.close()

if __name__ == "__main__":
    main()
