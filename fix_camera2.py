import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
ssh.connect('192.168.2.201', username='szy01', password='szy666')

# 恢复 98-0-0 记录，并删除跟司空2不匹配的 99-0-0
cmd = '''echo szy666 | sudo -S mysql -u root -p123456 sikong_cloud_api 2>/dev/null <<'SQL'
-- 先恢复被误删的 98-0-0
INSERT INTO aircraft_camera (aircraft_sn, camera_index, deleted, create_time, update_time)
SELECT '1581F8HHX25CL00A06BL', '98-0-0', 0, '2026-08-12 12:26:30', NOW()
WHERE NOT EXISTS (SELECT 1 FROM aircraft_camera WHERE aircraft_sn='1581F8HHX25CL00A06BL' AND camera_index='98-0-0');

-- 删除跟司空2不匹配的 99-0-0（司空2converter用的是98-0-0）
DELETE FROM aircraft_camera WHERE aircraft_sn='1581F8HHX25CL00A06BL' AND camera_index='99-0-0';

-- 查看结果
SELECT id, aircraft_sn, camera_index, create_time, update_time FROM aircraft_camera WHERE aircraft_sn='1581F8HHX25CL00A06BL';
SQL'''

print("=== 恢复 98-0-0, 删除 99-0-0 ===")
stdin, stdout, stderr = ssh.exec_command(cmd)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print("STDERR:", err)

ssh.close()
