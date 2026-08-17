import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. 最近2分钟的OSD状态
    ('最近OSD状态', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "3 minutes ago" 2>/dev/null | grep -E "OSD-mode|OSD-状态解析" | tail -20'),
    # 2. 航线任务相关日志
    ('航线任务日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "5 minutes ago" 2>/dev/null | grep -iE "wayline|航线|flighttask|flight_task|flight.*plan|takeoff|起飞|执行.*航线|航线.*执行" | tail -30'),
    # 3. MQTT接收到的命令
    ('MQTT命令日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "5 minutes ago" 2>/dev/null | grep -iE "services_reply|flighttask|wayline.*exec|takeoff.*reply|device.*reply" | tail -20'),
    # 4. 飞行任务表
    ('飞行任务表', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, flight_id, gateway_sn, aircraft_sn, status, step_code, error_msg, create_time, update_time FROM sikong_cloud_api.flight_task WHERE deleted=0 ORDER BY id DESC LIMIT 5;" 2>/dev/null'),
    # 5. 设备在线状态
    ('设备在线状态', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT device_sn, device_name, device_type, online_status, bound_status, mode_code FROM sikong_cloud_api.device WHERE deleted=0 AND device_sn IN (\'1581F8HHX25CL00A06BL\', \'8UUXN5U00A09RH\');" 2>/dev/null'),
    # 6. 后端最近的错误日志
    ('最近错误日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "5 minutes ago" 2>/dev/null | grep -iE "error|exception|失败|错误|refus|timeout|异常" | tail -20'),
    # 7. 飞机实时状态
    ('飞机实时状态', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT device_sn, device_mode, mode_code, flight_status, drone_in_dock, cover_state, flight_step_code, update_time FROM sikong_cloud_api.device_realtime_status WHERE device_sn IN (\'1581F8HHX25CL00A06BL\', \'8UUXN5U00A09RH\');" 2>/dev/null'),
    # 8. 最近的所有重要日志（不限关键词）
    ('最近所有INFO日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2 minutes ago" 2>/dev/null | grep -v "Preparing\|Parameters\|Columns\|Row:\|SqlSession\|JDBC Connection\|HikariProxy" | tail -40'),
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
