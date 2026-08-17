import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

# 删除错误的98-0-0记录，保留99-0-0
cmd = 'echo szy666 | sudo -S mysql -u root -p123456 -e "DELETE FROM sikong_cloud_api.aircraft_camera WHERE id=9 AND aircraft_sn=\'1581F8HHX25CL00A06BL\' AND camera_index=\'98-0-0\';" 2>/dev/null && echo "删除成功" && echo szy666 | sudo -S mysql -u root -p123456 -e "SELECT id, aircraft_sn, camera_index FROM sikong_cloud_api.aircraft_camera WHERE aircraft_sn=\'1581F8HHX25CL00A06BL\';" 2>/dev/null'

print("=== 执行删除 ===")
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print("STDERR:", err)

ssh.close()
