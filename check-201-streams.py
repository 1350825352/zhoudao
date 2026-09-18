import paramiko, requests

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
    # 查知眸 SRS 的 API
    run(ssh, "curl -s http://127.0.0.1:2022/api/v1/streams/ 2>&1 | head")
    # 查 docker 里的 SRS
    run(ssh, "echo szy666 | sudo -S docker ps 2>&1 | grep -i srs")
    run(ssh, "echo szy666 | sudo -S docker ps 2>&1")
    # 看知眸 SRS 配置
    run(ssh, "echo szy666 | sudo -S find /opt/zhimou -name 'srs.conf' 2>/dev/null")
    run(ssh, "echo szy666 | sudo -S cat /opt/zhimou/srs.conf 2>/dev/null | head -40")
    # 查看 /proc/3296/cwd 知道工作目录
    run(ssh, "echo szy666 | sudo -S ls -la /proc/3296/cwd 2>&1")
    run(ssh, "echo szy666 | sudo -S ls /opt/zhimou/ 2>&1")
    ssh.close()

    # 本机直接测试流能否访问
    print("\n=== 本机测试流 ===")
    urls = [
        "http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv",
        "http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv",
    ]
    for u in urls:
        try:
            r = requests.get(u, timeout=5, stream=True)
            print(f"  {u}  → {r.status_code}  content-type: {r.headers.get('Content-Type')}")
        except Exception as e:
            print(f"  {u}  → ERR: {e}")

if __name__ == "__main__":
    main()
