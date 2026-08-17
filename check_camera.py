import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 这架飞机在aircraft_camera表里的记录
    ('飞机的camera记录', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT * FROM sikong_cloud_api.aircraft_camera WHERE aircraft_sn=\'1581F8HHX25CL00A06BL\';" 2>/dev/null'),
    # 整个aircraft_camera表
    ('aircraft_camera全部', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, aircraft_sn, camera_index FROM sikong_cloud_api.aircraft_camera;" 2>/dev/null'),
    # 司空2当前converter列表的完整数据 - 看camera字段
    ('司空2当前converter完整', 'curl -s "http://127.0.0.1:8080/api/dji/stream-converters" 2>/dev/null'),
    # 正确的API路径
    ('司空2API路径2', 'curl -s "http://127.0.0.1:8080/api/openapi/v2.0/stream-converters" 2>/dev/null | head -200'),
    # 飞机当前OSD最新状态
    ('当前OSD', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "1 minutes ago" 2>/dev/null | grep -E "OSD-mode|OSD-状态解析" | tail -10'),
]

for title, cmd in commands:
    print(f"\n=== {title} ===")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if out:
        print(out[:3500])
    if err:
        print("STDERR:", err[:500])
    if not out and not err:
        print("(empty)")

ssh.close()
