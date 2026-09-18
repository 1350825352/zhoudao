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
    # 带 secret 的识别流
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'ai+secret %{http_code} %{content_type}\\n' 'http://192.168.2.201:2022/live/ai/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6' 2>&1")
    # 带 secret 的直播流
    run(ssh, "curl -s --max-time 3 -o /dev/null -w 'live+secret %{http_code} %{content_type}\\n' 'http://192.168.2.201:2022/live/1581F8HGX252F00A018X/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6' 2>&1")
    # 后端日志最近的探测
    run(ssh, "echo szy666 | sudo -S tail -200 /opt/sikong-cloud/logs/app.log 2>&1 | grep -iE '流探测|probe|recognition|识别' | tail -20")
    ssh.close()

if __name__ == "__main__":
    main()
