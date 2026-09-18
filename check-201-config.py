import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')
cmds = [
    'echo szy666 | sudo -S find /opt/sikong-cloud -name "application*.yml" -type f 2>/dev/null',
    'echo szy666 | sudo -S cat /opt/sikong-cloud/application-company-a.yml 2>/dev/null | grep -A 3 "h5:"',
    'echo szy666 | sudo -S cat /etc/systemd/system/sikong-cloud.service 2>/dev/null | grep -E "ExecStart|WorkingDir|spring.profiles'
]
for cmd in cmds:
    _, stdout, _ = ssh.exec_command(cmd)
    out = stdout.read().decode()
    print(f"CMD: {cmd[:60]}")
    print(out)
    print("---")
ssh.close()
