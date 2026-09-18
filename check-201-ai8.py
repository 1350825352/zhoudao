import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=10):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== {c}")
        if out.strip(): print(out)
    except Exception as e:
        print(f"\n=== {c}\n  TIMEOUT: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 调识别视频接口
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    # 后端日志 - 识别流探测
    run(ssh, "echo szy666 | sudo -S tail -200 /opt/sikong-cloud/logs/app.log 2>&1 | grep -iE '识别流|实时识别|流探测.*ai|live/ai|probeUrl|可播放.*ai' | tail -20")
    # 流探测 DEBUG 日志
    run(ssh, "echo szy666 | sudo -S tail -200 /opt/sikong-cloud/logs/app.log 2>&1 | grep '流探测-DEBUG' | tail -15")
    # live_stream 状态
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 3;' 2>&1 | grep -v Warning")
    # 识别流是否存在
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'ai-internal %{http_code} %{content_type}\\n' 'http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6' 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
