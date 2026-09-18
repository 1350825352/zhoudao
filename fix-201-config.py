import paramiko
ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')
# 在外部配置文件的 rtmp-url-ai 行后添加 pull-url-template-ai
cmd = 'echo szy666 | sudo -S sed -i \'/rtmp-url-ai:/a\\      pull-url-template-ai: http://223.113.158.186:2022/live/ai/{streamKey}.flv?secret={secret}\' /opt/sikong-cloud/config/application-company-a.yml && echo szy666 | sudo -S grep -A 2 "rtmp-url-ai" /opt/sikong-cloud/config/application-company-a.yml && echo szy666 | sudo -S systemctl restart sikong-cloud && sleep 2 && echo szy666 | sudo -S systemctl is-active sikong-cloud'
_, stdout, _ = ssh.exec_command(cmd)
print(stdout.read().decode())
ssh.close()
