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
    # 流是否存在
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'live %{http_code}\\n' http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'ai %{http_code}\\n' http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv 2>&1")
    # 后端重启后日志
    run(ssh, "echo szy666 | sudo -S tail -50 /opt/sikong-cloud/logs/app.log 2>&1 | grep -iE 'recognition|识别|video|stream' | tail -20")
    # live_stream 状态
    run(ssh, "mysql -uszy02 -p123456 sikong_cloud_api -e 'SELECT id,zhimou_status FROM live_stream ORDER BY id DESC LIMIT 3;' 2>&1 | grep -v Warning")
    ssh.close()

if __name__ == "__main__":
    main()
