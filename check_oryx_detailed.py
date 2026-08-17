import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 找 glob filter 配置位置：搜[123/123]
    ('Oryx 搜索glob filter 123配置', 'echo szy666 | sudo -S docker exec oryx sh -c "grep -rn \'123\' /usr/local/srs/conf/ /data/conf/ /usr/local/oryx/ 2>/dev/null | head -20" 2>/dev/null'),
    # Oryx 配置里没有glob就查Oryx管理接口 - API /api/v1/streams 返回空的真正原因
    ('Oryx 本机127.0.0.1:1985 API测试', 'echo szy666 | sudo -S docker exec oryx sh -c "curl -s http://127.0.0.1:1985/api/v1/streams/ 2>/dev/null | head -200" 2>/dev/null'),
    # SRS release.conf 中的 http hooks (on_publish, on_unpublish, on_play) 是谁实现的? 找2022端口服务
    ('Oryx容器内监听2022端口的程序是谁', 'echo szy666 | sudo -S docker exec oryx sh -c \"ss -tlnp 2>/dev/null | grep 2022 || netstat -tlnp 2>/dev/null | grep 2022\" 2>/dev/null'),
    # 无人机公网IP为什么从121.30.87.160变121.30.179.48? 是无人机切换网络了？！
    ('统计无人机公网推流IP', 'echo szy666 | sudo -S docker logs oryx --since "2026-08-12T04:30:00Z" --until "2026-08-12T04:45:00Z" 2>&1 | grep -oE "\"ip\":\"[^:]+\"" | sort | uniq -c | sort -rn | head'),
]

for title, cmd in commands:
    print(f"\n=== {title} ===")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode().strip()
    err = stderr.read().decode().strip()
    if out:
        print(out[:5000])
    if err:
        print("STDERR:", err[:800])
    if not out and not err:
        print("(empty)")

ssh.close()
