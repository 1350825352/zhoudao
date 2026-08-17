import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 1. 查看流检测失败的具体时间点和持续时间
    ('流检测失败汇总', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:40:30" 2>/dev/null | grep "可播放=false" | wc -l'),
    ('流检测成功汇总', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:40:30" 2>/dev/null | grep "可播放=true" | wc -l'),
    # 2. 查看后端推流到底用哪个地址(从日志看push_url)
    ('push_url样本', 'echo szy666 | sudo -S journalctl -u sikong-backend --no-pager --since "2026-08-12 12:38:00" --until "2026-08-12 12:39:00" 2>/dev/null | grep -oE "rtmp://[^\"]+live/[^\"]+" | head -5'),
    # 3. 网络稳定性 - ping外网IP
    ('ping 58.213.86.2', 'ping -c 5 -W 1 58.213.86.2 2>&1 | tail -3'),
    # 4. 网络稳定性 - ping内网
    ('ping 192.168.2.201', 'ping -c 5 -W 1 192.168.2.201 2>&1 | tail -3'),
    # 5. 检查aircraft_camera表是否存在
    ('aircraft_camera表', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SHOW TABLES LIKE \'aircraft_camera\';" 2>/dev/null'),
    ('aircraft_camera数据', 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT * FROM sikong_cloud_api.aircraft_camera LIMIT 5;" 2>/dev/null'),
    # 6. 测试从外网RTMP推流到SRS是否经过本机(58.213.86.2是否NAT到192.168.2.201)
    ('外网1935到本机', 'echo szy666 | sudo -S iptables -t nat -L PREROUTING -n 2>/dev/null | grep 1935 | head -5'),
    # 7. 检查Oryx容器的SRS日志 - 推流连接记录
    ('Oryx SRS日志', 'echo szy666 | sudo -S docker logs oryx --tail 50 2>&1 | grep -iE "rtmp|publish|stream|connect|client|error" | tail -20'),
    # 8. 检查Oryx SRS的1985端口在容器内是否监听
    ('Oryx容器端口', 'echo szy666 | sudo -S docker exec oryx sh -c "ss -tlnp 2>/dev/null || netstat -tlnp 2>/dev/null" 2>/dev/null | head -20'),
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
