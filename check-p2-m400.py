import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.208', username='humh01', password='1qaz2wsx')
cmds = [
    'docker exec p2-video ps aux | grep -E "ffmpeg|python" 2>&1',
    'docker exec p2-video tail -30 /var/log/uvicorn-err.log 2>&1',
    'docker exec p2-video tail -20 /var/log/nginx/access.log 2>&1',
    'curl -s http://192.168.2.201:2022/api/v1/streams/ 2>&1 | head -c 500',
]
for cmd in cmds:
    _, stdout, _ = ssh.exec_command(cmd)
    out = stdout.read().decode()
    print(f"=== {cmd[:60]} ===")
    print(out[-2000:])
    print()
ssh.close()
