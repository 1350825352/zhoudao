import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=10):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        # 只打印相关行
        lines = out.split('\n')
        relevant = [l for l in lines if any(k in l.lower() for k in ['recognition', '识别', 'video/', 'ai-rec', 'sn:', '内网='])]
        if relevant:
            for l in relevant[-20:]:
                print(l)
        else:
            print(out[-1500:] if len(out) > 1500 else out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 直接调后端识别视频接口
    run(ssh, "curl -s 'http://192.168.2.201:8080/api/uavs/1581F8HGX252F00A018X/video/recognition' 2>&1")
    # 后端日志 - 找视频流请求
    run(ssh, "echo szy666 | sudo -S grep -iE '视频流|识别流|recognition|ai.*pull|buildAi' /opt/sikong-cloud/logs/app.log 2>&1 | tail -20")
    # 看后端日志最近的 video 相关
    run(ssh, "echo szy666 | sudo -S grep -i 'video' /opt/sikong-cloud/logs/app.log 2>&1 | tail -20")
    ssh.close()

if __name__ == "__main__":
    main()
