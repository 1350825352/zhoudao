import paramiko, time

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        return out
    except:
        return ""

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    
    for i in range(8):
        print(f"\n===== #{i+1} =====")
        out = run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,status,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 1;' 2>&1 | grep -v Warning")
        print("DB:", out.strip())
        out = run(ssh, "echo szy666 | sudo -S grep '识别流-' /opt/sikong-cloud/logs/app.log 2>&1 | tail -5")
        print("识别流调试:", out.strip() if out.strip() else "(无)")
        out = run(ssh, "echo szy666 | sudo -S grep '识别流可播放' /opt/sikong-cloud/logs/app.log 2>&1 | tail -3")
        print("可播放:", out.strip() if out.strip() else "(无)")
        out = run(ssh, "curl -s --max-time 3 -o /dev/null -w '%{http_code}' 'http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6' 2>&1")
        print("识别流HTTP:", out.strip())
        if i < 7:
            time.sleep(8)
    ssh.close()

if __name__ == "__main__":
    main()
