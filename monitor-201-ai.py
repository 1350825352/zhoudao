import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        return out
    except Exception as e:
        return f"TIMEOUT: {e}"

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    
    for i in range(6):
        print(f"\n===== 检查 #{i+1} =====")
        # 1. 识别流调试日志
        out = run(ssh, "echo szy666 | sudo -S grep '识别流-' /opt/sikong-cloud/logs/app.log 2>&1 | tail -10")
        print("识别流调试:", out.strip() if out.strip() else "(无)")
        # 2. 流探测DEBUG（识别流）
        out = run(ssh, "echo szy666 | sudo -S grep '流探测-DEBUG.*live/ai' /opt/sikong-cloud/logs/app.log 2>&1 | tail -5")
        print("识别流探测:", out.strip() if out.strip() else "(无)")
        # 3. 识别流可播放但未稳定
        out = run(ssh, "echo szy666 | sudo -S grep '识别流可播放' /opt/sikong-cloud/logs/app.log 2>&1 | tail -5")
        print("识别流可播放:", out.strip() if out.strip() else "(无)")
        # 4. live_stream 状态
        out = run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 1;' 2>&1 | grep -v Warning")
        print("live_stream:", out.strip())
        # 5. 识别流是否存在
        out = run(ssh, "curl -s --max-time 3 -o /dev/null -w '%{http_code}' 'http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6' 2>&1")
        print("识别流HTTP状态:", out.strip())
        if i < 5:
            time.sleep(10)
    
    ssh.close()

if __name__ == "__main__":
    main()
