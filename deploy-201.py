import paramiko
import os

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"
LOCAL_JAR = r"h:\traeproject\zhoudao\target\sikong-cloud-api-1.0.0.jar"
REMOTE_JAR = "/home/szy01/sikong-cloud-api-1.0.0.jar"
SERVICE_NAME = "sikong-cloud"

def main():
    print(f"[1] 上传 jar 到 {HOST} ...")
    t = paramiko.Transport((HOST, 22))
    t.connect(username=USER, password=PWD)
    sftp = paramiko.SFTPClient.from_transport(t)
    sftp.put(LOCAL_JAR, REMOTE_JAR)
    stat = sftp.stat(REMOTE_JAR)
    print(f"    上传完成, 远端大小: {stat.st_size} bytes")
    sftp.close()
    t.close()

    print(f"[2] 重启服务 {SERVICE_NAME} ...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD)
    cmds = [
        f"echo {PWD} | sudo -S cp {REMOTE_JAR} /opt/sikong-cloud/sikong-cloud-api-1.0.0.jar",
        f"echo {PWD} | sudo -S systemctl restart {SERVICE_NAME}",
        f"sleep 3",
        f"systemctl status {SERVICE_NAME} --no pager -l",
    ]
    for c in cmds:
        stdin, stdout, stderr = ssh.exec_command(c, get_pty=True)
        out = stdout.read().decode(errors='replace')
        err = stderr.read().decode(errors='replace')
        print(f"$ {c}")
        if out: print(out)
        if err: print("ERR:", err)
    ssh.close()
    print("[done]")

if __name__ == "__main__":
    main()
