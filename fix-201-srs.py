import paramiko

HOST = "192.168.2.201"
USER = "szy01"
PWD = "szy666"

def run(ssh, c, timeout=30):
    stdin, stdout, stderr = ssh.exec_command(c, timeout=timeout, get_pty=True)
    out = stdout.read().decode(errors='replace')
    print(f"\n=== $ {c}")
    if out.strip(): print(out)

def main():
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PWD, timeout=10)

    # 1. 停掉并禁用 Docker Oryx
    print("[1] 停掉 Docker Oryx ...")
    run(ssh, "echo szy666 | sudo -S docker stop oryx 2>&1")
    run(ssh, "echo szy666 | sudo -S docker update --restart=no oryx 2>&1")

    # 2. 修改知眸 SRS 配置
    print("\n[2] 修改知眸 SRS 配置 ...")
    # 备份
    run(ssh, "echo szy666 | sudo -S cp /opt/zhimou/srs.conf /opt/zhimou/srs.conf.bak 2>&1")
    # listen 11935 → 1935
    run(ssh, "echo szy666 | sudo -S sed -i 's/^listen              11935;/listen              1935;/' /opt/zhimou/srs.conf 2>&1")
    # http_api listen 127.0.0.1:11985 → 0.0.0.0:1985
    run(ssh, "echo szy666 | sudo -S sed -i 's/listen          127.0.0.1:11985;/listen          0.0.0.0:1985;/' /opt/zhimou/srs.conf 2>&1")
    # http_server listen 127.0.0.1:18080 → 0.0.0.0:2022
    run(ssh, "echo szy666 | sudo -S sed -i 's/listen          127.0.0.1:18080;/listen          0.0.0.0:2022;/' /opt/zhimou/srs.conf 2>&1")
    # candidate 58.213.86.2 → 223.113.158.186
    run(ssh, "echo szy666 | sudo -S sed -i 's/candidate 58.213.86.2;/candidate 223.113.158.186;/' /opt/zhimou/srs.conf 2>&1")
    # 验证修改
    run(ssh, "echo szy666 | sudo -S grep -E 'listen|candidate' /opt/zhimou/srs.conf 2>&1")

    # 3. 重启知眸 SRS
    print("\n[3] 重启知眸 SRS ...")
    run(ssh, "echo szy666 | sudo -S systemctl restart aivis_srs 2>&1")
    import time; time.sleep(3)
    run(ssh, "echo szy666 | sudo -S systemctl status aivis_srs 2>&1 | head -15")

    # 4. 验证端口
    print("\n[4] 验证端口 ...")
    run(ssh, "echo szy666 | sudo -S ss -tlnp | grep -E '1935|2022|1985' 2>&1")

    # 5. 验证 SRS API
    print("\n[5] 验证 SRS API ...")
    run(ssh, "curl -s -u zhimou:qizhidata1 http://127.0.0.1:1985/api/v1/streams/ 2>&1")

    ssh.close()

if __name__ == "__main__":
    main()
