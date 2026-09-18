import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=15):
    try:
        stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout)
        stdout.channel.set_combine_stderr(True)
        out = stdout.read().decode(errors='replace')
        print(f"\n=== $ {c}")
        print(out)
    except Exception as e:
        print(f"\n=== $ {c}\n  TIMEOUT/ERR: {e}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # Oryx docker 日志 - 找 publish/publishing 记录
    run(ssh, "echo szy666 | sudo -S docker logs oryx --tail 500 2>&1 | grep -iE 'publish|stream.*name|connect.*app|ai/' | tail -30")
    # 找所有提到 ai 的日志
    run(ssh, "echo szy666 | sudo -S docker logs oryx --tail 500 2>&1 | grep -i '\\bai\\b' | tail -20")
    # 看 Oryx 管理 API - 先找 token
    run(ssh, "echo szy666 | sudo -S docker exec oryx env 2>&1 | grep -iE 'token|auth|secret|pass' | head -10")
    # Oryx 5.x 管理 API 路径
    run(ssh, "curl -sk https://192.168.2.201:2443/mgmt/ 2>&1 | head -10")
    run(ssh, "curl -sk https://192.168.2.201:2443/api/v1/streams 2>&1 | head -10")
    run(ssh, "curl -sk https://192.168.2.201:2443/mgmt/api/v1/streams 2>&1 | head -10")
    ssh.close()

if __name__ == "__main__":
    main()
