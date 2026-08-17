import paramiko

ssh = paramiko.SSHClient()
ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    ssh.connect('192.168.2.201', username='user', password='szy666', timeout=10)
    
    # Check current count
    stdin, stdout, stderr = ssh.exec_command('mysql -u root -p123456 sikong_cloud_api -e "SELECT COUNT(*) FROM zhimou_detection_result WHERE deleted=0;" 2>/dev/null')
    print('BEFORE:', stdout.read().decode().strip())
    
    # Delete work_order records first
    del_sql = "DELETE FROM work_order WHERE alarm_id NOT IN (SELECT id FROM (SELECT id FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC LIMIT 5) AS keep_ids);"
    stdin, stdout, stderr = ssh.exec_command(f'mysql -u root -p123456 sikong_cloud_api -e "{del_sql}" 2>/dev/null')
    out = stdout.read().decode().strip()
    print(f'Deleted work_order: {out}')
    
    # Delete detection_result records
    del_sql2 = "DELETE FROM zhimou_detection_result WHERE deleted=0 AND id NOT IN (SELECT id FROM (SELECT id FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC LIMIT 5) AS keep_ids);"
    stdin, stdout, stderr = ssh.exec_command(f'mysql -u root -p123456 sikong_cloud_api -e "{del_sql2}" 2>/dev/null')
    out2 = stdout.read().decode().strip()
    print(f'Deleted detection_result: {out2}')
    
    # Verify
    stdin, stdout, stderr = ssh.exec_command('mysql -u root -p123456 sikong_cloud_api -e "SELECT COUNT(*) FROM zhimou_detection_result WHERE deleted=0;" 2>/dev/null')
    print('AFTER:', stdout.read().decode().strip())
    
    # Show remaining records
    stdin, stdout, stderr = ssh.exec_command('mysql -u root -p123456 sikong_cloud_api -e "SELECT id, rule_name, event_time FROM zhimou_detection_result WHERE deleted=0 ORDER BY id DESC;" 2>/dev/null')
    print('REMAINING:')
    print(stdout.read().decode())
    
    # Restart backend
    stdin, stdout, stderr = ssh.exec_command('sudo systemctl restart sikong-backend')
    print('Restart result:', stdout.read().decode().strip(), stderr.read().decode().strip())
    
    ssh.close()
except Exception as e:
    print(f'Error: {e}')