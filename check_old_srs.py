import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

commands = [
    # 老SRS进程先确认
    ('老SRS进程确认', 'ps -p 3139 -o pid,cmd 2>/dev/null && echo "进程存在" || echo "进程不存在"'),
    # 先查老SRS监听的端口，确认不会跟Oryx冲突：11935,11985,18080 都是非标准端口
    ('老SRS监听端口', 'echo szy666 | sudo -S ss -tlnp 2>/dev/null | grep 3139 || echo szy666 | sudo -S netstat -tlnp 2>/dev/null | grep 3139'),
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
