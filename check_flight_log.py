import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. 飞行期间完整日志 - 推流相关
    ('飞行期推流日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:42:00" 2>/dev/null | grep -iE "推流|开码流|关码流|state.*running|state.*initialized|可播放|流检测|SRS流|live_start|live_stop|MQTT.*发送|services" | head -60'),
    # 2. 飞行期间OSD mode_code变化
    ('飞行期OSD变化', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:42:00" 2>/dev/null | grep "OSD-mode" | head -40'),
    # 3. 流检测结果变化（可播放true/false）
    ('流检测变化', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:42:30" 2>/dev/null | grep "流检测-结果" | head -40'),
    # 4. 司空2码流转发器state变化
    ('转发器state变化', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:42:30" 2>/dev/null | grep -oE "state\":\"[a-z]+\"" | sort | uniq -c'),
    # 5. camera_index 检查 - 数据库 vs 司空2
    ('camera_index对比', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT aircraft_sn, camera_index FROM sikong_cloud_api.aircraft_camera WHERE aircraft_sn=\"1581F8HHX25CL00A06BL\";" 2>/dev/null'),
    # 6. 检查Oryx容器的SRS配置 - API端口
    ('Oryx容器内部1985', 'echo szy666 | sudo -S docker exec oryx sh -c "curl -s http://127.0.0.1:1985/api/v1/streams/ 2>/dev/null || echo no-1985" 2>/dev/null'),
    # 7. Oryx容器内部端口
    ('Oryx内部端口', 'echo szy666 | sudo -S docker exec oryx sh -c "netstat -tlnp 2>/dev/null || ss -tlnp" 2>/dev/null | head -20'),
    # 8. 检查是否有两个SRS进程冲突
    ('所有SRS进程', 'ps aux | grep -i srs | grep -v grep'),
    # 9. 1985 vs 11985 - 哪个能查到流
    ('老SRS 11985流', 'curl -s "http://127.0.0.1:11985/api/v1/streams/"'),
    # 10. 后端配置中使用的探测地址
    ('后端RTMP配置', 'echo szy666 | sudo -S cat /opt/wurenji/backend/application-company-a.yml 2>/dev/null | grep -A 12 "media:"'),
]

for title, cmd in commands:
    print(f"\n=== {title} ===")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if out:
        print(out[:5000])
    if err:
        print("STDERR:", err[:500])
    if not out and not err:
        print("(empty)")

ssh.close()
