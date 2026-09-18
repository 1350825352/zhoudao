import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"$ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 1. 禁用旧的 sikong 配置（指向 /opt/wurenji 的旧路径）
    run(ssh, "echo szy666 | sudo -S rm -f /etc/nginx/sites-enabled/sikong 2>&1")
    # 2. 修改 sikong-cloud 的 admin alias 改为指向 dist 子目录
    # alias /opt/sikong-cloud/admin/ → alias /opt/sikong-cloud/admin/dist/
    run(ssh, "echo szy666 | sudo -S sed -i 's|alias /opt/sikong-cloud/admin/;|alias /opt/sikong-cloud/admin/dist/;|' /etc/nginx/sites-available/sikong-cloud 2>&1")
    # 同步 screen 和 h5 也指向 dist
    run(ssh, "echo szy666 | sudo -S sed -i 's|alias /opt/sikong-cloud/screen/;|alias /opt/sikong-cloud/screen/dist/;|' /etc/nginx/sites-available/sikong-cloud 2>&1")
    run(ssh, "echo szy666 | sudo -S sed -i 's|alias /opt/sikong-cloud/h5/;|alias /opt/sikong-cloud/h5/dist/;|' /etc/nginx/sites-available/sikong-cloud 2>&1")
    # try_files 里的 /admin/index.html 也要改
    run(ssh, "echo szy666 | sudo -S grep -n 'admin\\|screen\\|h5' /etc/nginx/sites-available/sikong-cloud 2>&1")
    # 3. 测试 nginx 配置
    run(ssh, "echo szy666 | sudo -S nginx -t 2>&1")
    # 4. 重载 nginx
    run(ssh, "echo szy666 | sudo -S systemctl reload nginx 2>&1")
    # 5. 验证
    run(ssh, "ls /opt/sikong-cloud/admin/dist/index.html /opt/sikong-cloud/screen/dist/index.html /opt/sikong-cloud/h5/dist/index.html 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
