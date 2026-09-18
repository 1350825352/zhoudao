import paramiko, requests

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=20):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 1. Oryx 状态
    run(ssh, "echo szy666 | sudo -S docker ps --format '{{.Names}} {{.Status}}' 2>&1 | grep oryx")
    # 2. 知眸 SRS 状态
    run(ssh, "echo szy666 | sudo -S systemctl is-active aivis_srs 2>&1")
    # 3. 后端状态
    run(ssh, "echo szy666 | sudo -S systemctl is-active sikong-cloud 2>&1")
    # 4. 端口检查
    run(ssh, "echo szy666 | sudo -S ss -tlnp | grep -E '1935|2022|8080' 2>&1")
    # 5. 直播流测试
    run(ssh, "curl -s -o /dev/null -w 'live %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 6. 后端健康
    run(ssh, "curl -s -o /dev/null -w 'backend %{http_code}\\n' http://192.168.2.201:8080/api/wayline/list 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
