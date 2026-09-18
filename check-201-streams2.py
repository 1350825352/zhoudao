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
    # 1. Oryx API (Docker 容器内部 1985 -> 宿主机可能没映射)
    run(ssh, "echo szy666 | sudo -S docker exec oryx curl -s http://127.0.0.1:1985/api/v1/streams/ 2>&1")
    # 2. Oryx 管理 API
    run(ssh, "curl -s http://192.168.2.201:2022/api/v1/streams/ 2>&1")
    run(ssh, "curl -s http://192.168.2.201:1985/api/v1/streams/ 2>&1")
    # 3. 知眸 SRS API (127.0.0.1:11985 带认证)
    run(ssh, "curl -s -u zhimou:qizhidata1 http://127.0.0.1:11985/api/v1/streams/ 2>&1")
    # 4. 知眸 SRS HTTP-FLV 测试
    run(ssh, "curl -s -o /dev/null -w '%{http_code} %{content_type}' -u zhimou:qizhidata1 http://127.0.0.1:18080/live/ai/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 5. 查后端 live_stream 表
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,aircraft_sn,camera_index,zhimou_status,create_time,push_url FROM live_stream ORDER BY id DESC LIMIT 5;' 2>&1 | grep -v Warning")
    # 6. 看后端日志关于视频流的
    run(ssh, "echo szy666 | sudo -S grep -i 'video.*recognition\\|识别流\\|buildAi\\|stream.*probe\\|2022' /opt/sikong-cloud/logs/app.log 2>&1 | tail -30")
    ssh.close()

if __name__ == "__main__":
    main()
