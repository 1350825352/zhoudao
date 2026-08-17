import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. 当前SRS流列表
    ('SRS当前流', 'curl -s "http://127.0.0.1:1985/api/v1/streams/" 2>/dev/null'),
    # 2. 当前 live_stream 最新状态
    ('当前live_stream', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, aircraft_sn, camera_index, status, status_message, zhimou_status, zhimou_task_id, zhimou_last_error, start_time, update_time FROM sikong_cloud_api.live_stream WHERE deleted=0 ORDER BY id DESC LIMIT 6;" 2>/dev/null'),
    # 3. 最近10分钟的OSD状态变化
    ('最近OSD状态', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep "OSD-mode" | tail -30'),
    # 4. 最近10分钟码流转发器/推流日志
    ('最近推流日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep -iE "开码流|关码流|推流|MQTT.*发送|state.*running|state.*initialized|创建.*直播|直播.*创建|camera_index" | tail -30'),
    # 5. 最近流检测结果
    ('最近流检测', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep "流检测-结果" | tail -20'),
    # 6. 司空2码流转发器当前状态
    ('司空2转发器状态', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "5 minutes ago" 2>/dev/null | grep -oE "state\":\"[a-z]+\"|camera\":\"[0-9-]+\"|device_online_status\":[a-z]+" | tail -20'),
    # 7. 后端是否正常运行
    ('后端服务状态', 'echo szy666 | sudo -S systemctl status sikong-backend --no-pager 2>/dev/null | head -10'),
    # 8. SRS进程和端口
    ('SRS进程端口', 'echo szy666 | sudo -S netstat -tlnp 2>/dev/null | grep -E "1935|2022|1985"'),
    # 9. 最近知眸任务日志
    ('最近知眸日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep -iE "知眸.*创建|知眸.*失败|知眸.*启动|zhimou.*error|zhimou.*fail|知眸-AI推流" | tail -20'),
    # 10. 当前时间
    ('当前时间', 'date "+%Y-%m-%d %H:%M:%S"'),
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
