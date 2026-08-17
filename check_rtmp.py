import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 检查 Oryx SRS 的 1985 端口（API）
    ('Oryx SRS API 1985', 'curl -s "http://127.0.0.1:1985/api/v1/streams/" && echo "" && curl -s "http://127.0.0.1:1985/api/v1/versions/"'),
    # 检查 Oryx Docker 容器
    ('Docker 容器', 'echo szy666 | sudo -S docker ps 2>/dev/null | grep -i srs'),
    # 检查 Oryx SRS 的所有端口映射
    ('Docker 端口映射', 'echo szy666 | sudo -S docker ps --format "{{.Names}} {{.Ports}}" 2>/dev/null | grep -i srs'),
    # 检查 1985 端口是否真的在监听
    ('1985端口监听', 'echo szy666 | sudo -S netstat -tlnp 2>/dev/null | grep 1985'),
    # 检查从本机到 RTMP 服务器 58.213.86.2:1935 的连通性
    ('RTMP 58.213.86.2:1935 连通', 'timeout 3 bash -c "echo > /dev/tcp/58.213.86.2/1935" 2>&1 && echo "可达" || echo "不可达"'),
    # 检查从本机到 192.168.2.201:1935 的连通性
    ('RTMP 192.168.2.201:1935 连通', 'timeout 3 bash -c "echo > /dev/tcp/192.168.2.201/1935" 2>&1 && echo "可达" || echo "不可达"'),
    # 检查 58.213.86.2 是否是本机的外网IP
    ('本机IP', 'ip addr show | grep "inet " | grep -v 127.0.0.1'),
    # 查看 Oryx SRS 的流（通过 2022 端口的播放页测试）
    ('Oryx 流测试-99-0-0', 'curl -s -o /dev/null -w "%{http_code}" "http://127.0.0.1:2022/live/1581F8HHX25CL00A06BL/99-0-0.flv?secret=f91c3c6060cd401b99ae649fc50a9bb6" --max-time 3 2>&1'),
    # 后端日志 - 码流转发器创建时的详细日志
    ('后端日志-创建码流', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager 2>/dev/null | grep -iE "创建码流|开码流|码流转发.*创建|converter.*create|state.*running|state.*initialized|MQTT.*发送|services" | tail -30'),
    # 当前 live_stream 最新状态
    ('当前live_stream', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, status, status_message, zhimou_status, zhimou_task_id, start_time, update_time FROM sikong_cloud_api.live_stream WHERE deleted=0 ORDER BY id DESC LIMIT 3;" 2>/dev/null'),
    # 检查 OSD 状态 - 飞机是否在飞
    ('OSD最近状态', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager 2>/dev/null | grep "OSD-mode" | tail -10'),
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
