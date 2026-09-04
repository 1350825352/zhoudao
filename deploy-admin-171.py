#!/usr/bin/env python3
"""上传新 admin dist 到 171"""
import paramiko

HOST = "192.168.2.171"
USER = "nvidia"
PASS = "nvidiaszyzkytj"

def main():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, username=USER, password=PASS, timeout=10)

    # 1. 上传 zip
    print("上传 admin-dist.zip...")
    sftp = c.open_sftp()
    sftp.put("h:/traeproject/zhoudao/admin-dist.zip", "/tmp/admin-dist.zip")
    print("上传完成")

    # 2. 替换文件
    print("\n替换 admin dist...")
    commands = [
        "rm -rf /opt/sikong-cloud/frontend/admin/*",
        "cd /opt/sikong-cloud/frontend/admin && unzip -o /tmp/admin-dist.zip",
        "rm -f /tmp/admin-dist.zip",
        "chmod -R 755 /opt/sikong-cloud/frontend/admin/",
        "ls /opt/sikong-cloud/frontend/admin/assets/ | head -5",
        "grep -l photoAlarm /opt/sikong-cloud/frontend/admin/assets/*.js 2>/dev/null || echo NOT_FOUND"
    ]

    for cmd in commands:
        full_cmd = f"echo '{PASS}' | sudo -S sh -c '{cmd}' 2>&1"
        stdin, stdout, stderr = c.exec_command(full_cmd)
        out = stdout.read().decode('utf-8', errors='replace').strip()
        err = stderr.read().decode('utf-8', errors='replace').strip()
        # 过滤 sudo 密码提示
        lines = [l for l in out.split('\n') if not l.startswith('[sudo]')]
        result = '\n'.join(lines).strip()
        if result:
            print(f"  {result}")

    sftp.close()

    # 3. 验证
    print("\n验证 Nginx...")
    stdin, stdout, stderr = c.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost/admin/")
    code = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"  admin 页面 HTTP: {code}")

    stdin, stdout, stderr = c.exec_command("curl -s -o /dev/null -w '%{http_code}' http://localhost/admin/assets/index-CP0ZK1hC.js")
    code2 = stdout.read().decode('utf-8', errors='replace').strip()
    print(f"  admin JS HTTP: {code2}")

    c.close()
    print("\n部署完成!")

if __name__ == "__main__":
    main()
