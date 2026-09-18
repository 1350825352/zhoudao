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
    # 测本机 localhost 用 szy02 登录
    run(ssh, "mysql -uszy02 -p123456 -e 'select user(),current_database();' 2>&1 | head")
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'show tables;' 2>&1 | head -25")
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'select count(*) from organization_project; select count(*) from wayline;' 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
