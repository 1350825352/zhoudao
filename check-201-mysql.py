import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c):
    stdin, stdout, stderr = ssh.exec_command(c)
    out = stdout.read().decode(errors='replace')
    err = stderr.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out: print(out, end="" if out.endswith("\n") else "\n")
    if err.strip(): print("ERR:", err)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD)
    run(ssh, "which mysql mysqld mariadb 2>/dev/null")
    run(ssh, "echo szy666 | sudo -S ss -ltnp 2>/dev/null | grep -E '3306|3307' || echo 'no 3306 listening'")
    run(ssh, "echo szy666 | sudo -S systemctl list-units --type=service --no-pager 2>/dev/null | grep -iE 'mysql|maria' || echo 'no mysql/mariadb service'")
    run(ssh, "echo szy666 | sudo -S dpkg -l 2>/dev/null | grep -iE 'mysql|maria' | head -20")
    run(ssh, "echo szy666 | sudo -S docker ps 2>/dev/null | head -20")
    run(ssh, "df -h | head -10")
    run(ssh, "free -h")
    ssh.close()

if __name__ == "__main__":
    main()
