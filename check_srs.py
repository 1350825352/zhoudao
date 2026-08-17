import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    ('SRS 进程', 'ps aux | grep -i srs | grep -v grep'),
    ('SRS 端口监听', 'echo szy666 | sudo -S netstat -tlnp 2>/dev/null | grep -E "1935|1985|2022|8080"'),
    ('SRS 流列表', 'curl -s "http://127.0.0.1:1985/api/v1/streams/"'),
    ('SRS 客户端', 'curl -s "http://127.0.0.1:1985/api/v1/clients/"'),
    ('SRS sessions', 'curl -s "http://127.0.0.1:1985/api/v1/sessions/"'),
    ('live_stream 详情(2964)', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, aircraft_sn, camera_index, status, status_message, push_url, pull_url, zhimou_status, zhimou_task_id, zhimou_start_time, zhimou_last_error, start_time, update_time FROM sikong_cloud_api.live_stream WHERE id=2964 \\G" 2>/dev/null'),
    ('后端日志-码流转发器创建', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager 2>/dev/null | grep -iE "码流转发|stream.converter|创建|create.*live|start.*live|push_url|pull_url" | tail -30'),
    ('后端日志-RTMP推流', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager 2>/dev/null | grep -iE "rtmp|推流" | tail -20'),
    ('后端日志-最近知眸', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager 2>/dev/null | grep -iE "知眸|zhimou" | tail -30'),
    ('司空2 码流转发器状态', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager 2>/dev/null | grep -iE "state.*running|converter.*state|stream.*running" | tail -10'),
    ('SRS 配置', 'echo szy666 | sudo -S cat /opt/wurenji/srs/conf/srs.conf 2>/dev/null || echo "未找到srs.conf"'),
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
