import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')
# 用 sed 修改 h5 配置
cmds = [
    'echo szy666 | sudo -S sed -i \'s/host: 192.168.2.201/host: 223.113.158.186/\' /opt/sikong-cloud/config/application-company-a.yml',
    'echo szy666 | sudo -S sed -i \'s/port: 3203/port: 33026/\' /opt/sikong-cloud/config/application-company-a.yml',
    'echo szy666 | sudo -S sed -i "/^h5:/,/^[^ ]/ { /port: 33026/a\\  context-path: h5" /opt/sikong-cloud/config/application-company-a.yml',
    'echo szy666 | sudo -S grep -A 3 "h5:" /opt/sikong-cloud/config/application-company-a.yml',
    'echo szy666 | sudo -S systemctl restart sikong-cloud',
    'sleep 2',
    'echo szy666 | sudo -S systemctl is-active sikong-cloud'
]
for cmd in cmds:
    _, stdout, _ = ssh.exec_command(cmd)
    out = stdout.read().decode()
    if out.strip():
        print(out.strip())
        print("---")
ssh.close()
print("Done")
