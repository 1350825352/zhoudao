import paramiko, os, stat

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"
LOCAL_DIST = r"h:\traeproject\zhoudao\qianduan\screen-v3\dist"
REMOTE_DIST = "/opt/sikong-cloud/screen/dist"

def upload_dir(sftp, local, remote):
    for item in os.listdir(local):
        lp = os.path.join(local, item)
        rp = remote + "/" + item
        if os.path.isdir(lp):
            try: sftp.mkdir(rp)
            except: pass
            upload_dir(sftp, lp, rp)
        else:
            sftp.put(lp, rp)
            print(f"  {rp}")

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    sftp = paramiko.SFTPClient.from_transport(ssh.get_transport())
    # 清空旧 dist
    stdin, stdout, stderr = ssh.exec_command(f"echo {PWD} | sudo -S rm -rf {REMOTE_DIST}/* 2>&1", get_pty=True)
    stdout.read()
    print("旧 dist 已清空")
    # 上传新 dist
    print("上传中...")
    upload_dir(sftp, LOCAL_DIST, REMOTE_DIST)
    # 设置权限
    stdin, stdout, stderr = ssh.exec_command(f"echo {PWD} | sudo -S chown -R szy01:szy01 {REMOTE_DIST} 2>&1", get_pty=True)
    stdout.read()
    print("权限已设置")
    sftp.close()
    ssh.close()
    print("部署完成")

if __name__ == "__main__":
    main()
