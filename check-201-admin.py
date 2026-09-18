import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=20):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
    stdout.channel.set_combine_stderr(True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    run(ssh, "ls -la /opt/sikong-cloud/admin/ 2>/dev/null")
    run(ssh, "find /opt/sikong-cloud/admin -name 'index.html' 2>/dev/null | head")
    run(ssh, "ls -la /opt/sikong-cloud/admin/assets/ 2>/dev/null | head -10")
    # nginx 配置
    run(ssh, "echo szy666 | sudo -S grep -l 'admin\\|sikong' /etc/nginx/sites-enabled/* /etc/nginx/conf.d/* 2>/dev/null")
    run(ssh, "echo szy666 | sudo -S cat /etc/nginx/sites-enabled/default 2>/dev/null | head -60")
    ssh.close()

if __name__ == "__main__":
    main()
