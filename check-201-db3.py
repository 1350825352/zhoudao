import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=20):
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
    # 用 sudo 方式直接进入 mysql（root unix_socket）
    run(ssh, "echo szy666 | sudo -S mysql -e 'show databases;' 2>&1 | head -30")
    run(ssh, "echo szy666 | sudo -S mysql -e \"select user,host,plugin from mysql.user;\" 2>&1 | head -30")
    run(ssh, "echo szy666 | sudo -S mysql -e \"show variables like 'bind%'; show variables like 'port';\" 2>&1")
    run(ssh, "grep -i 'mysql' /home/szy01/.bash_history 2>/dev/null | tail -30")
    ssh.close()

if __name__ == "__main__":
    main()
