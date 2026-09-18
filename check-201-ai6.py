import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=10):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        if out.strip(): print(out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 1. 调后端识别视频接口
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    # 2. 流是否存在
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'ai %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'live %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 3. 后端日志 - 识别流探测
    run(ssh, "echo szy666 | sudo -S tail -100 /opt/sikong-cloud/logs/app.log 2>&1 | grep -iE '识别流|recognition|ai.*flv|live/ai|流探测.*ai|buildAi' | tail -15")
    # 4. 后端日志 - 最近视频流请求
    run(ssh, "echo szy666 | sudo -S tail -100 /opt/sikong-cloud/logs/app.log 2>&1 | grep -iE '视频流|实时识别' | tail -10")
    # 5. live_stream 状态
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 3;' 2>&1 | grep -v Warning")
    ssh.close()

if __name__ == "__main__":
    main()
