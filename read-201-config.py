import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')
_, stdout, _ = ssh.exec_command('echo szy666 | sudo -S cat /opt/sikong-cloud/config/application-company-a.yml 2>/dev/null')
print(stdout.read().decode())
ssh.close()
