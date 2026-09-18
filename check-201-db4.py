import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"
MYSQL_PWD = "123456"

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
    run(ssh, f"mysql -uroot -p{MYSQL_PWD} -e 'show databases;' 2>&1 | head")
    run(ssh, f"mysql -uroot -p{MYSQL_PWD} sikong_cloud_api -e 'show tables;' 2>&1 | head -50")
    run(ssh, f"mysql -uroot -p{MYSQL_PWD} sikong_cloud_api -e 'select count(*) as cnt from organization_project; select count(*) as cnt from wayline;' 2>&1")
    run(ssh, f"mysql -uroot -p{MYSQL_PWD} -e \"select user,host,plugin from mysql.user where user in ('root','sikong','szy02','replica');\" 2>&1")
    run(ssh, "grep -E 'bind|port' /etc/mysql/mysql.conf.d/mysqld.cnf 2>/dev/null")
    ssh.close()

if __name__ == "__main__":
    main()
