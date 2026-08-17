import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 飞行期（12:38-12:40:30）探测异常日志
    ('飞行期探测异常', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:40:30" 2>/dev/null | grep -iE "流探测.*异常|流探测.*DEBUG.*可播放=false|可播放=false" | head -20'),
    # 看看所有"Header不匹配"的情况
    ('飞行期Header不匹配', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:40:30" 2>/dev/null | grep "Header不匹配" | head -10'),
    # 看看网络相关的异常 (Connection refused, Connect timeout, Read timed out, Socket)
    ('飞行期网络异常', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:40:30" 2>/dev/null | grep -iE "流探测.*异常|Connect|timeout|refused|reset|Socket" | head -20'),
    # 检查Oryx容器内部SRS的HTTP-FLV 1985/2022端口日志是否有断连
    ('Oryx飞行期日志', 'echo szy666 | sudo -S docker logs oryx --since "2026-08-12T04:38:00Z" --until "2026-08-12T04:40:30Z" 2>&1 | grep -iE "error|warn|disconnect|timeout|publish|unpublish" | tail -30'),
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
