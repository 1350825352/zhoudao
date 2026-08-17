import paramiko
import time

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. 司空2当前converter状态(通过后端日志看，因为直接API路径404)
    ('后端最近converter状态', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2 minutes ago" 2>/dev/null | grep -oE "state\":\"[a-z]+\"|camera\":\"[0-9-]+\"|device_online_status\":[a-z]+|converter_id\":\"[a-f0-9-]+\"" | tail -20'),
    # 2. 最新live_stream状态
    ('最新live_stream', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, status, status_message, update_time FROM sikong_cloud_api.live_stream WHERE deleted=0 ORDER BY id DESC LIMIT 3;" 2>/dev/null'),
    # 3. 当前SRS流
    ('SRS当前流', 'curl -s "http://127.0.0.1:1985/api/v1/streams/" 2>/dev/null'),
    # 4. 飞机当前OSD mode_code
    ('飞机当前mode', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "30 seconds ago" 2>/dev/null | grep "OSD-状态解析.*1581F8HHX25CL00A06BL" | tail -3'),
]

for title, cmd in commands:
    print(f"\n=== {title} ===")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if out:
        print(out[:3000])
    if err:
        print("STDERR:", err[:500])
    if not out and not err:
        print("(empty)")

ssh.close()
