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
    # 看后端日志最近 500 行里和识别相关的
    run(ssh, "echo szy666 | sudo -S tail -500 /opt/sikong-cloud/logs/app.log 2>&1 | grep -iE '识别流|rec.*可播放|rec.*稳定|rec.*失败|live/ai|ai.*flv' | tail -20")
    # 看 3386 liveId 相关的知眸 AI 推流
    run(ssh, "echo szy666 | sudo -S grep 'liveId=3386' /opt/sikong-cloud/logs/app.log 2>&1 | tail -10")
    # 直接看最近 30 行原始日志
    run(ssh, "echo szy666 | sudo -S tail -30 /opt/sikong-cloud/logs/app.log 2>&1")
    ssh.close()

if __name__ == "__main__":
    main()
