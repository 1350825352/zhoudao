import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')
cmd = 'echo szy666 | sudo -S grep -E "流探测|可播放|识别流可播放|STREAM_STABLE|连续成功|available" /opt/sikong-cloud/logs/app.log 2>/dev/null | tail -15'
_, stdout, _ = ssh.exec_command(cmd)
print(stdout.read().decode()[-2000:])
ssh.close()
