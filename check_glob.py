import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. Oryx容器中glob filter的配置
    ('Oryx SRS配置文件', 'echo szy666 | sudo -S docker exec oryx sh -c "find / -name srs.conf -o -name srs.release.conf 2>/dev/null | head -5" 2>/dev/null'),
    ('Oryx srs.release.conf', 'echo szy666 | sudo -S docker exec oryx sh -c "cat /usr/local/oryx/platform/containers/conf/srs.release.conf 2>/dev/null || find /usr/local -name *.conf | xargs grep -l glob 2>/dev/null | head -3" 2>/dev/null'),
    # 2. 看看glob filter配置内容
    ('Oryx glob filter配置', 'echo szy666 | sudo -S docker exec oryx sh -c "grep -r -A 1 glob /usr/local/oryx/ 2>/dev/null | head -30" 2>/dev/null'),
    # 3. 老的zhimou srs.conf 配置（/opt/zhimou/srs -c srs.conf 启动目录）
    ('老zhimou srs工作目录', 'ls -la /opt/zhimou/ 2>/dev/null | head -10'),
    ('老zhimou srs.conf', 'echo szy666 | sudo -S cat /proc/3139/cwd/../srs.conf 2>/dev/null | head -100'),
    # 4. Oryx unpublish 前后的时间段对比 - ip 121.30.87.160是无人机的公网推流IP对吧？
    ('无人机IP确认', 'echo "无人机推流IP是日志中的 121.30.87.160" && echo szy666 | sudo -S docker logs oryx --tail 500 2>&1 | grep -oE "ip=121\.30\.[0-9.]+" | sort -u'),
]

for title, cmd in commands:
    print(f"\n=== {title} ===")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if out:
        print(out[:4000])
    if err:
        print("STDERR:", err[:500])
    if not out and not err:
        print("(empty)")

ssh.close()
