import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.208', username='humh01', password='1qaz2wsx')
cmds = [
    'docker exec p2-video cat /etc/nginx/conf.d/p2.conf 2>&1',
    'docker exec p2-video cat /etc/nginx/nginx.conf 2>&1 | head -60',
    'docker exec p2-video tail -10 /var/log/nginx/error.log 2>&1',
]
for cmd in cmds:
    _, stdout, _ = ssh.exec_command(cmd)
    out = stdout.read().decode()
    print(f"=== {cmd[:60]} ===")
    print(out[-2000:])
    print()
ssh.close()
