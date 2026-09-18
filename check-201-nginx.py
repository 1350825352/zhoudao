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
    run(ssh, "echo szy666 | sudo -S cat /etc/nginx/sites-enabled/sikong 2>&1")
    run(ssh, "echo szy666 | sudo -S cat /etc/nginx/sites-enabled/sikong-cloud 2>&1")
    # 看实际 admin dist 里 index.html 的 hash，和本机对比
    run(ssh, "cat /opt/sikong-cloud/admin/dist/index.html | head -20")
    run(ssh, "ls /opt/sikong-cloud/admin/dist/assets/ | grep -i 'index.*js' | head -5")
    # 看 nginx 是否还有其他 admin 配置
    run(ssh, "echo szy666 | sudo -S grep -rn 'admin' /etc/nginx/ 2>/dev/null | head -20")
    ssh.close()

if __name__ == "__main__":
    main()
