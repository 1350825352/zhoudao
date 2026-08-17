import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # glob filter来自include的srs.vhost.conf，看这个文件内容
    ('srs.vhost.conf 内容', 'echo szy666 | sudo -S docker exec oryx sh -c "cat containers/data/config/srs.vhost.conf 2>/dev/null || find / -name srs.vhost.conf 2>/dev/null" 2>/dev/null'),
    # 查看 /data/conf/srs.conf
    ('/data/conf/srs.conf', 'echo szy666 | sudo -S docker exec oryx sh -c "cat /data/conf/srs.conf 2>/dev/null | head -80" 2>/dev/null'),
    # Oryx verify接口是什么？它可能是触发unpublish的原因
    ('verify服务状态', 'echo szy666 | sudo -S docker ps --format "{{.Names}} {{.Ports}}" 2>/dev/null | head -10'),
    # Oryx Lighthouse主后端服务（处理terraform hooks的verify）
    ('Lighthouse进程', 'echo szy666 | sudo -S docker exec oryx sh -c "ps aux 2>/dev/null | grep -iE terraform|lighth|mgmt|node|python|go | head -15" 2>/dev/null'),
    # 真正关键：glob filter忽略流以后，Oryx会不会主动unpublish？看看日志上下文
    ('Oryx 12:38-12:40完整日志(关键时段)', 'echo szy666 | sudo -S docker logs oryx --since "2026-08-12T04:38:00Z" --until "2026-08-12T04:40:30Z" 2>&1 | grep -iE "glob|on_publish|on_unpublish|ignore|publish|unpublish|rtmp.*connect|tcUrl|stream.*create|stream.*destroy|verify|reject|121\.30\.87\.160" | head -60'),
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
