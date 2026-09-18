import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
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
    # 直播流和识别流（加 --max-time 3）
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'live %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'ai %{http_code} %{content_type}\\n' http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 知眸 SRS 流列表
    run(ssh, "curl -s -u zhimou:qizhidata1 http://127.0.0.1:11985/api/v1/streams/ 2>&1")
    # 后端日志 - 识别流探测
    run(ssh, "echo szy666 | sudo -S grep -iE '识别流|recognition|live/ai|ai.*flv' /opt/sikong-cloud/logs/app.log 2>&1 | tail -15")
    # Oryx docker 日志
    run(ssh, "echo szy666 | sudo -S docker logs oryx --tail 50 2>&1 | grep -iE 'publish|ai|connect' | tail -10")
    # live_stream 表
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,aircraft_sn,zhimou_status,push_url FROM live_stream ORDER BY id DESC LIMIT 3;' 2>&1 | grep -v Warning")
    ssh.close()

if __name__ == "__main__":
    main()
