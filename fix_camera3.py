import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

# 用单条SQL删除99-0-0
cmd = 'echo szy666 | sudo -S mysql -u root -p123456 -e "DELETE FROM sikong_cloud_api.aircraft_camera WHERE aircraft_sn=\'1581F8HHX25CL00A06BL\' AND camera_index=\'99-0-0\';" 2>/dev/null && echo "删除99-0-0成功"'

print("=== 删除 99-0-0 ===")
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print("STDERR:", err)

# 验证
cmd2 = 'echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, aircraft_sn, camera_index FROM sikong_cloud_api.aircraft_camera WHERE aircraft_sn=\'1581F8HHX25CL00A06BL\';" 2>/dev/null'
print("\n=== 验证结果 ===")
stdin, stdout, stderr = ssh.exec_command(cmd2)
print(stdout.read().decode())

ssh.close()
