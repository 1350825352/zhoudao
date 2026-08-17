import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. 最近5分钟的航线任务下发请求
    ('航线下发请求', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep -iE "下发|flighttask.*create|flight.*create|create.*flight|执行.*航线|航线.*执行|takeoff|prepare|准备|step_code|flighttask.*exe" | tail -30'),
    # 2. 司空2 OpenAPI 调用日志
    ('OpenAPI调用日志', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep -iE "DjiOpenApiClient|openapi.*post|flighttask.*api|api.*flighttask" | tail -20'),
    # 3. 最近的 flight_task 表
    ('flight_task表', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, task_uuid, task_name, task_status, status_message, step_code, result_code, create_time, update_time FROM sikong_cloud_api.flight_task WHERE deleted=0 ORDER BY id DESC LIMIT 5;" 2>/dev/null'),
    # 4. 飞机+机场详细状态
    ('设备详细状态', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT device_sn, device_name, online_status, mode_code, drone_in_dock, cover_state, flight_step_code, battery_percent, current_task_id FROM sikong_cloud_api.device_realtime_status WHERE device_sn IN (\'1581F8HHX25CL00A06BL\', \'8UUXN5U00A09RH\');" 2>/dev/null'),
    # 5. 最近MQTT services_reply (司空2回复)
    ('MQTT回复', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "10 minutes ago" 2>/dev/null | grep -iE "services_reply|flighttask.*reply|flight.*reply|takeoff.*reply" | tail -20'),
    # 6. 看下flight_task的request和response
    ('flight_task详情', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, task_name, task_status, status_message, request_json, response_json FROM sikong_cloud_api.flight_task WHERE deleted=0 ORDER BY id DESC LIMIT 2 \\G" 2>/dev/null'),
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
