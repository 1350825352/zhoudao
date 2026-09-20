import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.208', username='humh01', password='1qaz2wsx')
cmds = [
    'docker ps --format "{{.Names}}\t{{.Status}}\t{{.Ports}}" 2>&1',
    'docker logs --tail 10 p2-video 2>&1',
    'curl -s http://localhost:58999/ 2>&1 | head -c 300',
    'docker exec p2-video ps aux | grep -E "uvicorn|nginx" 2>&1',
]
for cmd in cmds:
    _, stdout, _ = ssh.exec_command(cmd)
    out = stdout.read().decode()
    print(f"=== {cmd[:60]} ===")
    print(out[-1500:])
    print()
ssh.close()
