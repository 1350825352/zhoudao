import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

cmd = 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, aircraft_sn, camera_index, create_time, update_time FROM sikong_cloud_api.aircraft_camera WHERE aircraft_sn=\'1581F8HHX25CL00A06BL\';" 2>/dev/null'

print("=== 当前 aircraft_camera 记录 ===")
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print("STDERR:", err)

ssh.close()
