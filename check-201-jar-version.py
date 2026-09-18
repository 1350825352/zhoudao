import paramiko, os

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"
LOCAL_JAR = r"h:\traeproject\zhoudao\target\sikong-cloud-api-1.0.0.jar"

def run(ssh, c, timeout=20):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    # 1. jar 文件时间
    run(ssh, "ls -la /opt/sikong-cloud/sikong-cloud.jar")
    # 2. 从 jar 提取关键类，看是否有 buildAiRecognitionPullUrl 方法
    run(ssh, "echo szy666 | sudo -S unzip -p /opt/sikong-cloud/sikong-cloud.jar BOOT-INF/classes/application-company-a.yml 2>&1 | grep -A1 'rtmp-url-ai'")
    # 3. 从 jar 提取 MediaServerStreamProbeServiceImpl.class 看是否包含最新代码
    run(ssh, "echo szy666 | sudo -S unzip -l /opt/sikong-cloud/sikong-cloud.jar 2>&1 | grep -i 'MediaServerStreamProbe'")
    # 4. 本机 jar 的 MD5
    local_md5 = os.popen(f"certutil -hashfile \"{LOCAL_JAR}\" MD5").read()
    print(f"\n本机 jar MD5: {local_md5.strip()}")
    # 5. 服务器 jar 的 MD5
    run(ssh, "md5sum /opt/sikong-cloud/sikong-cloud.jar")
    ssh.close()

if __name__ == "__main__":
    main()
