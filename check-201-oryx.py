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
    # Oryx 管理 API - 尝试不同端口和路径
    run(ssh, "curl -s http://192.168.2.201:2022/api/v1/streams/ 2>&1")
    run(ssh, "curl -s http://192.168.2.201:2443/api/v1/streams/ 2>&1")
    # Oryx 可能用 1985 但需要 docker 端口映射
    run(ssh, "echo szy666 | sudo -S docker port oryx 2>&1")
    # Oryx 管理 UI 可能是 2443 或 8080
    run(ssh, "curl -sk https://192.168.2.201:2443/ 2>&1 | head -5")
    # 尝试 Oryx API token 认证
    run(ssh, "curl -s -H 'Authorization: Bearer qizhidata1' http://192.168.2.201:2022/api/v1/streams/ 2>&1")
    # 看不带 secret 的直播流
    run(ssh, "curl -s -o /dev/null -w 'live %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 看带 secret 的直播流
    run(ssh, "curl -s -o /dev/null -w 'live+secret %{http_code} %{content_type}\\n' 'http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6' 2>&1")
    # 看识别流不带 secret
    run(ssh, "curl -s -o /dev/null -w 'ai %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 看 Oryx 里所有可能的 ai 路径
    run(ssh, "curl -s -o /dev/null -w 'ai2 %{http_code}\\n' http://192.168.2.201:2022/live/ai.flv 2>&1")
    run(ssh, "curl -s -o /dev/null -w 'ai3 %{http_code}\\n' http://192.168.2.201:2022/ai/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 看 Oryx docker 日志，找推流记录
    run(ssh, "echo szy666 | sudo -S docker logs oryx --tail 30 2>&1 | grep -iE 'publish|stream|ai|live' | tail -20")
    ssh.close()

if __name__ == "__main__":
    main()
