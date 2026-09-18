import paramiko, os, stat

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"
LOCAL_DIST = r"h:\traeproject\zhoudao\qianduan\admin-v3\dist"
REMOTE_DIST = "/home/szy01/admin-dist"
REMOTE_TARGET = "/opt/sikong-cloud/admin/dist"

def upload_dir(sftp, local, remote):
    for item in os.listdir(local):
        lpath = os.path.join(local, item)
        rpath = remote + "/" + item
        if os.path.isdir(lpath):
            try: sftp.mkdir(rpath)
            except: pass
            upload_dir(sftp, lpath, rpath)
        else:
            sftp.put(lpath, rpath)

def main():
    print(f"[1] 上传 admin dist 到 {HOST} ...")
    t = paramiko.Transport((HOST, 22))
    t.connect(username=USER, password=PWD)
    sftp = paramiko.SFTPClient.from_transport(t)
    # 清空远端临时目录
    try: sftp.stat(REMOTE_DIST)
    except: sftp.mkdir(REMOTE_DIST)
    # 先清空
    def clean_remote(path):
        try:
            for f in sftp.listdir(path):
                fp = path + "/" + f
                try:
                    sftp.stat(fp)
                    if (sftp.stat(fp).st_mode & stat.S_IFDIR):
                        clean_remote(fp); sftp.rmdir(fp)
                    else: sftp.remove(fp)
                except Exception as e: print(f"skip {fp}: {e}")
        except Exception as e: print(f"clean err {path}: {e}")
    clean_remote(REMOTE_DIST)
    upload_dir(sftp, LOCAL_DIST, REMOTE_DIST)
    sftp.close(); t.close()
    print("    上传完成")

    print(f"[2] 替换 {REMOTE_TARGET} ...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)
    cmds = [
        f"echo {PWD} | sudo -S rm -rf {REMOTE_TARGET}",
        f"echo {PWD} | sudo -S cp -r {REMOTE_DIST} {REMOTE_TARGET}",
        f"echo {PWD} | sudo -S chown -R szy01:szy01 {REMOTE_TARGET}",
        f"ls -la {REMOTE_TARGET}/assets/ | head -5",
        f"ls {REMOTE_TARGET}/index.html",
    ]
    for c in cmds:
        stdin, stdout, stderr = ssh.exec_command(c, get_pty=True)
        out = stdout.read().decode(errors='replace')
        print(f"$ {c}")
        if out.strip(): print(out)
    ssh.close()
    print("[done]")

if __name__ == "__main__":
    main()
