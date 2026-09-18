import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, sudo=False):
    cmd = f"echo {PWD} | sudo -S {c}" if sudo else c
    stdin, stdout, stderr = ssh.exec_command(cmd, get_pty=sudo)
    out = stdout.read().decode(errors='replace')
    err = stderr.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out: print(out, end="" if out.endswith("\n") else "\n")
    if err.strip() and "password" not in err.lower(): print("ERR:", err)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD)
    # 看 201 上 mysql 配置和现有库
    run(ssh, "cat /etc/mysql/mysql.conf.d/mysqld.cnf 2>/dev/null | grep -E 'bind|port|datadir' | head")
    run(ssh, "echo szy666 | sudo -S grep -r 'password' /etc/mysql/debian.cnf 2>/dev/null; cat /etc/mysql/debian.cnf 2>/dev/null")
    run(ssh, "ls -la /home/szy01/*.sql /home/szy01/*.db 2>/dev/null")
    # 尝试用空密码/root 常见密码登录
    for pwd in ["", "root", "123456", "szy666", "mysql", "123"]:
        stdin, stdout, stderr = ssh.exec_command(f"mysql -uroot -p'{pwd}' -e 'show databases;' 2>&1")
        out = stdout.read().decode(errors='replace')
        if "Database" in out and "ERROR" not in out:
            print(f"\n=== root 密码 = '{pwd}' 可登录")
            print(out)
            break
    else:
        print("\n=== 常见密码都登录失败，需要查找 root 密码")
        run(ssh, "echo szy666 | sudo -S cat /root/.mysql_root_password 2>/dev/null || echo 'no password file'")
        run(ssh, "echo szy666 | sudo -S cat /root/mysql_root_password.txt 2>/dev/null || echo 'no'")
        run(ssh, "echo szy666 | sudo -S grep -r 'password' /root/.my.cnf 2>/dev/null; cat /home/szy01/.my.cnf 2>/dev/null")
    ssh.close()

if __name__ == "__main__":
    main()
